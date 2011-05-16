// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.appengine.api.backends.dev.LocalServerController;
import com.google.appengine.tools.development.AbstractContainerService.LocalInitializationEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.utils.config.BackendsXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMapBuilder;
import com.google.common.collect.Maps;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.mortbay.jetty.webapp.WebAppContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Controls backend servers configured in appengine-web.xml. Each server is
 * started on a separate port. All servers run the same code as the main app.
 *
 *
 */
public class BackendServers implements BackendContainer, LocalServerController {
  private static final String X_GOOGLE_DEV_APPSERVER_SKIPADMINCHECK =
      "X-Google-DevAppserver-SkipAdminCheck";
  public static final String SYSTEM_PROPERTY_STATIC_PORT_NUM_PREFIX =
      "com.google.appengine.devappserver.";

  private static final int AH_REQUEST_DEFAULT_TIMEOUT = 30 * 1000;
  private static final int AH_REQUEST_INFINITE_TIMEOUT = 0;

  private static final Integer DEFAULT_INSTANCES = new Integer(1);
  private static final String DEFAULT_INSTANCE_CLASS = "B1";
  private static final Integer DEFAULT_MAX_CONCURRENT_REQUESTS = 10;

  private enum BackendServerState {
    INITIALIZING, SLEEPING, RUNNING_START_REQUEST, RUNNING, STOPPING, STOPPED, SHUTDOWN;
  }

  private static final int MAX_PENDING_QUEUE_LENGTH = 20;
  private static final int MAX_PENDING_QUEUE_TIME_MS = 10 * 1000;
  private static final int MAX_START_QUEUE_TIME_MS = 30 * 1000;

  private static BackendServers instance = new BackendServers();

  public static BackendServers getInstance() {
    return instance;
  }

  private String address;
  private File appDir;
  private String appEngineWebXmlFileName;
  private Map<BackendServers.ServerInstanceEntry, ServerWrapper> backendServers =
      ImmutableMapBuilder.fromMap(new HashMap<ServerInstanceEntry, ServerWrapper>()).getMap();
  private Map<String, String> portMapping =
      ImmutableMapBuilder.fromMap(new HashMap<String, String>()).getMap();
  private Logger logger = Logger.getLogger(BackendServers.class.getName());

  private Map<String, String> serviceProperties = new HashMap<String, String>();

  @VisibleForTesting
  BackendServers() {
  }

  public void init(File appDir, String appEngineWebXml, String address) {
    this.appDir = appDir;
    this.appEngineWebXmlFileName = appEngineWebXml;
    this.address = address;
  }

  @Override
  public void setServiceProperties(Map<String, String> properties) {
    this.serviceProperties = properties;
  }

  /**
   * Shutdown all backend servers
   *
   * @throws Exception
   */
  @Override
  public void shutdownAll() throws Exception {
    for (Iterator<ServerWrapper> iter = backendServers.values().iterator(); iter.hasNext();) {
      ServerWrapper server = iter.next();
      logger.finer("server shutdown: " + server);
      server.shutdown();
    }
    backendServers =
        ImmutableMapBuilder.fromMap(new HashMap<ServerInstanceEntry, ServerWrapper>()).getMap();
  }

  /**
   * Method used to get information about configured servers as well as their
   * current state.
   *
   * @return Server name to server state mapping for all servers.
   */
  @Override
  public TreeMap<String, BackendStateInfo> getBackendState(String requestHostName) {
    TreeMap<String, BackendStateInfo> serverInfoMap = new TreeMap<String, BackendStateInfo>();
    for (ServerWrapper serverWrapper : backendServers.values()) {
      String name = serverWrapper.serverEntry.getName();

      String listenAddress;
      if (requestHostName == null) {
        listenAddress = portMapping.get(serverWrapper.getDnsPrefix());
      } else {
        listenAddress = requestHostName + ":" + serverWrapper.port;
      }

      BackendStateInfo ssi = serverInfoMap.get(name);
      if (ssi == null) {
        ssi = new BackendStateInfo(serverWrapper.serverEntry);
        serverInfoMap.put(name, ssi);
      }
      if (serverWrapper.isLoadBalanceServer()) {
        ssi.setState(serverWrapper.serverState.name().toLowerCase());
        ssi.setAddress(listenAddress);
      } else {
        ssi.add(new InstanceStateInfo(serverWrapper.serverInstance, listenAddress,
            serverWrapper.serverState.name().toLowerCase()));
      }
    }
    return serverInfoMap;
  }

  @Override
  public synchronized void startBackend(String serverToStart) throws IllegalStateException {
    if (!checkServerExists(serverToStart)) {
      String message = String.format("Tried to start unknown server %s", serverToStart);
      logger.warning(message);
      throw new IllegalStateException(message);
    }
    for (ServerWrapper server : backendServers.values()) {
      if (server.getName().equals(serverToStart)) {
        if (server.getState() != BackendServerState.STOPPED) {
          continue;
        }
        if (server.isLoadBalanceServer()) {
          server.compareAndSetServerState(
              BackendServerState.RUNNING, BackendServerState.STOPPED);
          continue;
        }
        server.compareAndSetServerState(
            BackendServerState.SLEEPING, BackendServerState.STOPPED);
        server.sendStartRequest();
      }
    }
  }

  @Override
  public synchronized void stopBackend(String serverToStop)
      throws IllegalStateException, Exception {
    if (!checkServerExists(serverToStop)) {
      String message = String.format("Tried to stop unknown server %s", serverToStop);
      logger.warning(message);
      throw new IllegalStateException(message);
    }
    for (ServerWrapper server : backendServers.values()) {
      if (server.getName().equals(serverToStop)) {
        if (server.getState() == BackendServerState.STOPPED) {
          continue;
        }
        if (server.isLoadBalanceServer()) {
          server.compareAndSetServerState(
              BackendServerState.STOPPED, BackendServerState.RUNNING);
          continue;
        }
        logger.fine("Stopping server: " + server.getDnsPrefix());
        server.shutdown();
        server.startup(true);
      }
    }
  }

  /**
   * Start all backend servers, the number of servers to start is specified
   * in the {@code appConfig} parameter
   *
   * @param appConfig Parsed appengine-web.xml file with servers configuration
   * @throws Exception
   */
  @Override
  public void startupAll(BackendsXml backendsXml) throws Exception {
    if (backendsXml == null) {
      logger.fine("Got null backendsXml config.");
      return;
    }
    List<BackendsXml.Entry> servers = backendsXml.getBackends();
    if (servers.size() == 0) {
      logger.fine("No backends configured.");
      return;
    }

    if (backendServers.size() != 0) {
      throw new Exception("Tried to start backendservers but some are already running.");
    }
    logger.finer("Found " + servers.size() + " configured backends.");

    Map<BackendServers.ServerInstanceEntry, ServerWrapper> serverMap = Maps.newHashMap();
    for (BackendsXml.Entry entry : servers) {
      entry = resolveDefaults(entry);

      for (int serverInstance = -1; serverInstance < entry.getInstances(); serverInstance++) {
        int port = checkForStaticPort(entry.getName(), serverInstance);
        ServerWrapper serverWrapper =
            new ServerWrapper(ContainerUtils.loadContainer(), entry, serverInstance, port);
        serverMap.put(new ServerInstanceEntry(entry.getName(), serverInstance), serverWrapper);
      }
    }
    this.backendServers = ImmutableMapBuilder.fromMap(serverMap).getMap();

    String prettyAddress = address;
    if ("0.0.0.0".equals(address)) {
      prettyAddress = "127.0.0.1";
    }

    Map<String, String> portMap = Maps.newHashMap();
    for (ServerWrapper serverWrapper : backendServers.values()) {
      logger.finer(
          "starting server: " + serverWrapper.serverInstance + "." + serverWrapper.getName()
              + " on " + address + ":" + serverWrapper.port);
      serverWrapper.startup(false);

      portMap.put(serverWrapper.getDnsPrefix(), prettyAddress + ":" + serverWrapper.port);
    }
    this.portMapping = ImmutableMapBuilder.fromMap(portMap).getMap();

    for (ServerWrapper serverWrapper : backendServers.values()) {
      if (serverWrapper.isLoadBalanceServer()) {
        continue;
      }
      serverWrapper.sendStartRequest();
    }
  }

  private BackendsXml.Entry resolveDefaults(BackendsXml.Entry entry) {
    return new BackendsXml.Entry(
        entry.getName(),
        entry.getInstances() == null ? DEFAULT_INSTANCES : entry.getInstances(),
        entry.getInstanceClass() == null ? DEFAULT_INSTANCE_CLASS : entry.getInstanceClass(),
        entry.getMaxConcurrentRequests() == null ? DEFAULT_MAX_CONCURRENT_REQUESTS :
                                                   entry.getMaxConcurrentRequests(),
        entry.getOptions(),
        entry.getState() == null ? BackendsXml.State.STOP : entry.getState());
  }

  /**
   * Forward a request to a specific server and instance. This will call the
   * specified instance request dispatcher so the request is handled in the
   * right server context.
   */
  void forwardToServer(String requestedServer, int instance, HttpServletRequest hrequest,
      HttpServletResponse hresponse) throws IOException, ServletException {
    ServerWrapper server = getServerWrapper(requestedServer, instance);
    logger.finest("forwarding request to server: " + server);
    WebAppContext jettyContext =
        (WebAppContext) server.container.getAppContext().getContainerContext();
    RequestDispatcher requestDispatcher =
        jettyContext.getServletContext().getRequestDispatcher(hrequest.getRequestURI());
    requestDispatcher.forward(hrequest, hresponse);
  }

  /**
   * This method guards access to servers to limit the number of concurrent
   * requests. Each request running on a server must acquire a serving permit.
   * If no permits are available a 500 response should be sent.
   *
   * @param serverName The server for which to acquire a permit.
   * @param instanceNumber The server instance for which to acquire a permit.
   * @param allowQueueOnBackends If set to false the method will return
   *        instantly, if set to true (and the specified server allows pending
   *        queues) this method can block for up to 10 s waiting for a serving
   *        permit to become available.
   * @return true if a permit was acquired, false otherwise
   */
  boolean acquireServingPermit(
      String serverName, int instanceNumber, boolean allowQueueOnBackends) {
    logger.finest(
        String.format("trying to get serving permit for server %d.%s", instanceNumber, serverName));
    try {
      ServerWrapper server = getServerWrapper(serverName, instanceNumber);
      int maxQueueTime = 0;

      synchronized (server) {
        if (!server.acceptsConnections()) {
          logger.finest(server + ": got request but server is not in a serving state");
          return false;
        }
        if (server.getApproximateQueueLength() > MAX_PENDING_QUEUE_LENGTH) {
          logger.finest(server + ": server queue is full");
          return false;
        }
        if (server.getState() == BackendServerState.SLEEPING) {
          logger.finest(server + ": waking up sleeping server");
          server.sendStartRequest();
        }

        if (server.getState() == BackendServerState.RUNNING_START_REQUEST) {
          maxQueueTime = MAX_START_QUEUE_TIME_MS;
        } else if (allowQueueOnBackends && server.getMaxPendingQueueSize() > 0) {
          maxQueueTime = MAX_PENDING_QUEUE_TIME_MS;
        }
      }
      boolean gotPermit = server.acquireServingPermit(maxQueueTime);
      logger.finest(server + ": tried to get server permit, timeout=" + maxQueueTime + " success="
          + gotPermit);
      return gotPermit;
    } catch (InterruptedException e) {
      logger.finest(
          instanceNumber + "." + serverName + ": got interrupted while waiting for serving permit");
      return false;
    }
  }

  /**
   * Reserves an instance for this request. For workers this method will return
   * -1 if no free instances are available. For backends this method will assign
   * this request to the instance with the shortest queue and block until that
   * instance is ready to serve the request.
   *
   * @param requestedServer Name of the server the request is to.
   * @return the instance id of an available server instance, or -1 if no
   *         instance is available.
   */
  int getAndReserveFreeInstance(String requestedServer) {
    logger.finest("trying to get serving permit for server " + requestedServer);

    ServerWrapper server = getServerWrapper(requestedServer, -1);
    if (server == null) {
      return -1;
    }
    if (!server.acceptsConnections()) {
      return -1;
    }
    int instanceNum = server.getInstances();
    for (int i = 0; i < instanceNum; i++) {
      if (acquireServingPermit(requestedServer, i, false)) {
        return i;
      }
    }
    if (server.getMaxPendingQueueSize() > 0) {
      return addToShortestInstanceQueue(requestedServer);
    } else {
      logger.finest("no servers free");
      return -1;
    }
  }

  /**
   * Will add this request to the queue of the instance with the approximate
   * shortest queue. This method will block for up to 10 seconds until a permit
   * is received.
   *
   * @param requestedServer the server name
   * @return the instance where the serving permit was reserved, or -1 if all
   *         instance queues are full
   */
  int addToShortestInstanceQueue(String requestedServer) {
    logger.finest(requestedServer + ": no instances free, trying to find a queue");
    int shortestQueue = MAX_PENDING_QUEUE_LENGTH;
    ServerWrapper instanceWithShortestQueue = null;
    for (ServerWrapper server : backendServers.values()) {
      if (!server.acceptsConnections()) {
        continue;
      }
      int serverQueue = server.getApproximateQueueLength();
      if (shortestQueue > serverQueue) {
        instanceWithShortestQueue = server;
        shortestQueue = serverQueue;
      }
    }

    try {
      if (shortestQueue < MAX_PENDING_QUEUE_LENGTH) {
        logger.finest("adding request to queue on instance: " + instanceWithShortestQueue);
        if (instanceWithShortestQueue.acquireServingPermit(MAX_PENDING_QUEUE_TIME_MS)) {
          logger.finest("ready to serve request on instance: " + instanceWithShortestQueue);
          return instanceWithShortestQueue.serverInstance;
        }
      }
    } catch (InterruptedException e) {
      logger.finer("interupted while queued at server " + instanceWithShortestQueue);
    }
    return -1;
  }

  /**
   * Method for returning a serving permit after a request has completed.
   *
   * @param serverName The server name
   * @param instance The server instance
   */
  void returnServingPermit(String serverName, int instance) {
    ServerWrapper server = getServerWrapper(serverName, instance);
    server.releaseServingPermit();
  }

  /**
   * Verifies if a specific server/instance is configured.
   *
   * @param serverName The server name
   * @param instance The server instance
   * @return true if the server/instance is configured, false otherwise.
   */
  boolean checkInstanceExists(String serverName, int instance) {
    return getServerWrapper(serverName, instance) != null;
  }

  /**
   * Verifies if a specific server is configured.
   *
   * @param serverName The server name
   * @return true if the server is configured, false otherwise.
   */
  boolean checkServerExists(String serverName) {
    return checkInstanceExists(serverName, -1);
  }

  /**
   * Verifies if a specific server is stopped.
   *
   * @param serverName The server name
   * @return true if the server is stopped, false otherwise.
   */
  boolean checkServerStopped(String serverName) {
    return checkInstanceStopped(serverName, -1);
  }

  /**
   * Verifies if a specific server/instance is stopped.
   *
   * @param serverName The server name
   * @param instance The server instance
   * @return true if the server/instance is stopped, false otherwise.
   */
  boolean checkInstanceStopped(String serverName, int instance) {
    return !getServerWrapper(serverName, instance).acceptsConnections();
  }

  /**
   * Allows the servers API to get the current mapping from server and instance
   * to listening ports.
   *
   * @return The port map
   */
  Map<String, String> getPortMapping() {
    return this.portMapping;
  }

  /**
   * Returns the server instance serving on a specific local port
   *
   * @param port the local tcp port that received the request
   * @return the server instance, or -1 if no server instance is running on that
   *         port
   */
  int getServerInstanceFromPort(int port) {
    ServerWrapper server = getServerWrapperFromPort(port);
    if (server != null) {
      return server.serverInstance;
    } else {
      return -1;
    }
  }

  /**
   * Returns the server serving on a specific local port
   *
   * @param port the local tcp port that received the request
   * @return the server name, or null if no server instance is running on that
   *         port
   */
  String getServerNameFromPort(int port) {
    ServerWrapper server = getServerWrapperFromPort(port);
    if (server != null) {
      return server.getName();
    } else {
      return null;
    }
  }

  /**
   * Convenience method for getting the ServerWrapper running on a specific port
   */
  private ServerWrapper getServerWrapperFromPort(int port) {
    for (Entry<ServerInstanceEntry, ServerWrapper> entry : backendServers.entrySet()) {
      if (entry.getValue().port == port) {
        return entry.getValue();
      }
    }
    return null;
  }

  /**
   * Convenience method for getting the ServerWrapper for a specific
   * server/instance
   */
  private ServerWrapper getServerWrapper(String serverName, int instanceNumber) {
    return backendServers.get(new ServerInstanceEntry(serverName, instanceNumber));
  }

  /**
   * Verifies if the specific port is statically configured, if not it will
   * return 0 which instructs jetty to pick the port
   *
   *  Ports can be statically configured by a system property at
   * com.google.appengine.server.<server-name>.port for the server and
   * com.google.appengine.server.<server-name>.<instance-id>.port for individual
   * instances
   *
   * @param server the name of the configured server
   * @param instance the instance number to configure
   * @return the statically configured port, or 0 if none is configured
   */
  private int checkForStaticPort(String server, int instance) {
    StringBuilder key = new StringBuilder();
    key.append(SYSTEM_PROPERTY_STATIC_PORT_NUM_PREFIX);
    key.append(server);
    if (instance >= 0) {
      key.append("." + instance);
    }
    key.append(".port");
    String configuredPort = serviceProperties.get(key.toString());
    if (configuredPort != null) {
      return Integer.parseInt(configuredPort);
    } else {
      return 0;
    }
  }

  /**
   * Class that allows the key in the server map to be the
   * (servername,instanceid) tuple. Overrides equals() and hashcode() to
   * function as a hashtable key
   *
   */
  static class ServerInstanceEntry {
    private final int instanceNumber;
    private final String serverName;

    /**
     * @param serverName
     * @param instanceNumber
     */
    public ServerInstanceEntry(String serverName, int instanceNumber) {
      this.serverName = serverName;
      this.instanceNumber = instanceNumber;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof ServerInstanceEntry)) {
        return false;
      }

      ServerInstanceEntry that = (ServerInstanceEntry) o;
      if (this.serverName != null) {
        if (!this.serverName.equals(that.serverName)) {
          return false;
        }
      } else {
        if (that.serverName != null) {
          return false;
        }
      }

      if (this.instanceNumber != that.instanceNumber) {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode() {
      int hash = 17;
      hash = 31 * hash + instanceNumber;
      if (serverName != null) {
        hash = 31 * hash + serverName.hashCode();
      }
      return hash;
    }

    @Override
    public String toString() {
      return instanceNumber + "." + serverName;
    }
  }

  /**
   * Wraps a container service and contains extra information such as the
   * instanceid of the current container as well as the port number it is
   * running on.
   */
  private class ServerWrapper {

    private ContainerService container;
    private int serverInstance;
    private int port;
    private BackendsXml.Entry serverEntry;

    private BackendServerState serverState = BackendServerState.SHUTDOWN;

    private Semaphore servingQueue = new Semaphore(0, true);

    public ServerWrapper(ContainerService containerService, BackendsXml.Entry serverEntry,
        int instance, int port) {
      this.container = containerService;
      this.serverEntry = serverEntry;
      this.serverInstance = instance;
      this.port = port;
      containerService.setEnvironmentVariableMismatchSeverity(
          ContainerService.EnvironmentVariableMismatchSeverity.IGNORE);
    }

    /**
     * Triggers an HTTP GET to /_ah/start
     *
     *  This method will keep on trying until it receives a non-error response
     * code from the server.
     *
     * @param timeoutInMs Timeout in milliseconds, 0 indicates no timeout.
     *
     */
    private void sendStartRequest(int timeoutInMs) {
      try {
        String urlString =
            String.format("http://%s:%d/_ah/start", address, ServerWrapper.this.port);
        logger.finer("sending start request to: " + urlString);

        HttpClient httpClient = new HttpClient();
        httpClient.getParams().setConnectionManagerTimeout(timeoutInMs);

        GetMethod request = new GetMethod(urlString);
        request.addRequestHeader(X_GOOGLE_DEV_APPSERVER_SKIPADMINCHECK, "true");
        try {
          int returnCode = httpClient.executeMethod(request);

          byte[] buffer = new byte[1024];
          InputStream in = request.getResponseBodyAsStream();
          while (in.read(buffer) != -1) {
          }
          if ((returnCode >= 200 && returnCode < 300) || returnCode == 404) {
            logger.fine(
                String.format("backend server %d.%s request to /_ah/start completed, code=%d",
                    serverInstance, serverEntry.getName(), returnCode));
            compareAndSetServerState(
                BackendServerState.RUNNING, BackendServerState.RUNNING_START_REQUEST);
            servingQueue.release(serverEntry.getMaxConcurrentRequests());
          } else {
            logger.warning("Start request to /_ah/start on server " + serverInstance + "."
                + serverEntry.getName() + " failed (HTTP status code=" + returnCode
                + "). Retrying...");
            Thread.sleep(1000);
            sendStartRequest(timeoutInMs);
          }
        } finally {
          request.releaseConnection();
        }
      } catch (MalformedURLException e) {
        logger.severe(String.format(
            "Unable to send start request to server: %d.%s, " + "MalformedURLException: %s",
            serverInstance, serverEntry.getName(), e.getMessage()));
      } catch (Exception e) {
        logger.warning(String.format(
            "Got exception while performing /_ah/start " + "request on server: %d.%s, %s: %s",
            serverInstance, serverEntry.getName(), e.getClass().getName(), e.getMessage()));
      }
    }

    /**
     * Shut down the server.
     *
     * Will trigger any shutdown hooks installed by the
     * {@link com.google.appengine.api.LifecycleManager}
     *
     * @throws Exception
     */
    public void shutdown() throws Exception {
      synchronized (ServerWrapper.this) {
        if (serverState == BackendServerState.RUNNING
            || serverState == BackendServerState.RUNNING_START_REQUEST) {
          triggerLifecycleShutdownHook();
        }
        container.shutdown();
        serverState = BackendServerState.SHUTDOWN;
      }
    }

    void startup(boolean setStateToStopped) throws Exception {
      compareAndSetServerState(BackendServerState.INITIALIZING, BackendServerState.SHUTDOWN);

      AppEngineWebXmlReader appEngineWebXmlReader = null;
      if (appEngineWebXmlFileName != null) {
        appEngineWebXmlReader =
            new AppEngineWebXmlReader(appDir.getAbsolutePath(), appEngineWebXmlFileName);
      }

      container.configure(ContainerUtils.getServerInfo(),
          appDir,
          appEngineWebXmlFileName,
          appEngineWebXmlReader,
          address,
          port);
      container.startup();

      this.port = container.getPort();
      if (setStateToStopped) {
        compareAndSetServerState(BackendServerState.STOPPED, BackendServerState.INITIALIZING);
      } else {
        logger.info(
            "server: " + serverInstance + "." + serverEntry.getName() + " is running on port "
                + this.port);
        if (isLoadBalanceServer()) {
          compareAndSetServerState(
              BackendServerState.RUNNING, BackendServerState.INITIALIZING);
        } else {
          compareAndSetServerState(
              BackendServerState.SLEEPING, BackendServerState.INITIALIZING);
        }
      }
    }

    void sendStartRequest() {
      compareAndSetServerState(
          BackendServerState.RUNNING_START_REQUEST, BackendServerState.SLEEPING);
      if (serverInstance >= 0) {
        Thread requestThread = new Thread(new Runnable() {
          @Override
          public void run() {
            sendStartRequest(AH_REQUEST_INFINITE_TIMEOUT);
          }
        });
        requestThread.setDaemon(true);
        requestThread.setName(
            "BackendServersStartRequestThread." + serverInstance + "." + serverEntry.getName());
        requestThread.start();
      }
    }

    /**
     * This method will trigger any shutdown hooks registered with the current
     * server.
     *
     * Some class loader trickery is required to make sure that we get the
     * {@link com.google.appengine.api.LifecycleManager} responsible for this
     * server instance.
     */
    private void triggerLifecycleShutdownHook() {
      Environment prevEnvironment = ApiProxy.getCurrentEnvironment();
      try {
        ClassLoader serverClassLoader = container.getAppContext().getClassLoader();

        Class<?> lifeCycleManagerClass =
            Class.forName("com.google.appengine.api.LifecycleManager", true, serverClassLoader);
        Method lifeCycleManagerGetter = lifeCycleManagerClass.getMethod("getInstance");
        Object userThreadLifeCycleManager = lifeCycleManagerGetter.invoke(null, new Object[0]);

        Method beginShutdown = lifeCycleManagerClass.getMethod("beginShutdown", long.class);
        ApiProxy.setEnvironmentForCurrentThread(
            new LocalInitializationEnvironment(container.getAppEngineWebXmlConfig()));

        try {
          beginShutdown.invoke(userThreadLifeCycleManager, AH_REQUEST_DEFAULT_TIMEOUT);
        } catch (Exception e) {
          logger.warning(
              String.format("got exception when running shutdown hook on server %d.%s",
                  serverInstance, serverEntry.getName()));
          e.printStackTrace();
        }
      } catch (Exception e) {
        logger.severe(
            String.format("Exception during reflective call to "
                + "LifecycleManager.beginShutdown on server %d.%s, got %s: %s", serverInstance,
                serverEntry.getName(), e.getClass().getName(), e.getMessage()));
      } finally {
        ApiProxy.setEnvironmentForCurrentThread(prevEnvironment);
      }
    }

    /**
     * Checks if the server is in a state where it can accept incoming requests.
     *
     * @return true if the server can accept incoming requests, false otherwise.
     */
    boolean acceptsConnections() {
      synchronized (ServerWrapper.this) {
        return (serverState == BackendServerState.RUNNING
            || serverState == BackendServerState.RUNNING_START_REQUEST
            || serverState == BackendServerState.SLEEPING);
      }
    }

    /**
     * Updates the current server state and verifies that the previous state is
     * what is expected.
     *
     * @param newState The new state to change to
     * @param acceptablePreviousStates Acceptable previous states
     * @throws IllegalStateException If the current state is not one of the
     *         acceptable previous states
     */
    private void compareAndSetServerState(
        BackendServerState newState, BackendServerState... acceptablePreviousStates)
        throws IllegalStateException {
      synchronized (ServerWrapper.this) {
        for (BackendServerState acceptableStates : acceptablePreviousStates) {
          if (serverState == acceptableStates) {
            serverState = newState;
            return;
          }
        }
      }
      StringBuilder error = new StringBuilder();
      error.append("Tried to change state to " + newState);
      error.append(" on server " + toString());
      error.append(" but previous state is not ");
      for (int i = 0; i < acceptablePreviousStates.length; i++) {
        error.append(acceptablePreviousStates[i].name() + " | ");
      }
      throw new IllegalStateException(error.toString());
    }

    /**
     * Acquires a serving permit for this server.
     *
     * @param maxWaitTimeInMs Max wait time in ms
     * @return true if a serving permit was acquired within the allowed time,
     *         false if not permit was required.
     * @throws InterruptedException If the thread was interrupted while waiting.
     */
    boolean acquireServingPermit(int maxWaitTimeInMs) throws InterruptedException {
      logger.finest(
          this + ": accuiring serving permit, available: " + servingQueue.availablePermits());
      return servingQueue.tryAcquire(maxWaitTimeInMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns a serving permit to the pool of available permits
     */
    void releaseServingPermit() {
      servingQueue.release();
      logger.finest(
          this + ": returned serving permit, available: " + servingQueue.availablePermits());
    }

    /**
     * Returns the approximate number of threads waiting for serving permits.
     *
     * @return the number of waiting threads.
     */
    int getApproximateQueueLength() {
      return servingQueue.getQueueLength();
    }

    /**
     * Returns the number of requests that can be queued on this specific
     * server. For servers without pending queue no queue (size=0) is allowed.
     */
    int getMaxPendingQueueSize() {
      return serverEntry.isFailFast() ? 0 : MAX_PENDING_QUEUE_LENGTH;
    }

    /**
     * The dns prefix for this server, basically the first part of:
     * <instance>.<server_name>.<app-id>.appspot.com for a specific instance,
     * and <server_name>.<app-id>.appspot.com for just the server.
     */
    public String getDnsPrefix() {
      if (!isLoadBalanceServer()) {
        return serverInstance + "." + getName();
      } else {
        return getName();
      }
    }

    String getName() {
      return serverEntry.getName();
    }

    public int getInstances() {
      return serverEntry.getInstances();
    }

    BackendServerState getState() {
      synchronized (ServerWrapper.this) {
        return serverState;
      }
    }

    boolean isLoadBalanceServer() {
      return serverInstance == -1;
    }

    @Override
    public String toString() {
      return serverInstance + "." + serverEntry.getName() + " state=" + serverState;
    }
  }
}
