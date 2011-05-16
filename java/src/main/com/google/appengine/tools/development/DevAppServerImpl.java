// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import static com.google.appengine.tools.development.ContainerService.EnvironmentVariableMismatchSeverity.ERROR;
import static com.google.appengine.tools.development.ContainerService.EnvironmentVariableMismatchSeverity.WARNING;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.BindException;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code DevAppServer} launches a local Jetty server (by default) with a single
 * hosted web application.  It can be invoked from the command-line by
 * providing the path to the directory in which the application resides as the
 * only argument.
 *
 * Over time, the environment provided by this class should come to
 * resemble the environment provided to hosted applications in
 * production.  For example, stub applications of all of the API's
 * should be provided, and similar security restrictions should be
 * enforced.
 *
 */
class DevAppServerImpl implements DevAppServer {

  private final LocalServerEnvironment environment;

  private Map<String, String> serviceProperties = new HashMap<String, String>();

  private Logger logger = Logger.getLogger(DevAppServerImpl.class.getName());

  enum ServerState { INITIALIZING, RUNNING, STOPPING, SHUTDOWN }

  /**
   * The current state of the server.
   */
  private ServerState serverState = ServerState.INITIALIZING;

  /**
   * This is the main container containing the main (default) server
   */
  private ContainerService mainContainer = null;

  /**
   * Contains the backend servers configured as part of the "Servers" feature.
   * Each backend server is started on a separate port and keep their own
   * internal state. Memcache, datastore, and other API services are shared by
   * all servers, including the "main" server.
   */
  private final BackendContainer backendContainer;

  /**
   * Constructs a development application server that runs the single
   * application located in the given directory.  The application is
   * configured by reading <appDir>/WEB-INF/web.xml and
   * <appDir>/WEB-INF/appengine-web.xml.
   */
  public DevAppServerImpl(File appDir) {
    this(appDir, DEFAULT_HTTP_ADDRESS, DEFAULT_HTTP_PORT);
  }

  /**
   * Constructs a development application server that runs the single
   * application located in the given directory.  The application is
   * configured by reading <appDir>/WEB-INF/web.xml and
   * <appDir>/WEB-INF/appengine-web.xml.
   */
  public DevAppServerImpl(File appDir, String address, int port) {
    this(appDir, null, null, address, port, true);
  }

  /**
   * Constructs a development application server that runs the single
   * application located in the given directory.  The application is configured
   * via <webXmlLocation> and the {@link AppEngineWebXml}
   * instance returned by the provided {@link AppEngineWebXmlReader}.
   *
   * @param appDir The location of the application to run.
   * @param webXmlLocation The location of a file whose format complies with
   * http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd.  Can be null.
   * @param appEngineWebXml The name of the app engine config file, relative to
   * the WEB-INF directory.  If {@code null},
   * {@link AppEngineWebXmlReader#DEFAULT_FILENAME} is used.
   * @param address The address on which to run
   * @param port The port on which to run
   * @param useCustomStreamHandler If {@code true}, install
   * {@link StreamHandlerFactory}.  This is "normal" behavior
   * for the dev app server.
   */
  public DevAppServerImpl(File appDir, String webXmlLocation,
      String appEngineWebXml, String address, int port, boolean useCustomStreamHandler) {
    String serverInfo = ContainerUtils.getServerInfo();
    if (useCustomStreamHandler) {
      StreamHandlerFactory.install();
    }
    mainContainer = ContainerUtils.loadContainer();
    AppEngineWebXmlReader appEngineWebXmlReader = null;
    if (appEngineWebXml != null) {
      appEngineWebXmlReader = new AppEngineWebXmlReader(appDir.getAbsolutePath(), appEngineWebXml);
    }
    environment = mainContainer.configure(
        serverInfo, appDir, webXmlLocation, appEngineWebXmlReader, address, port);

    backendContainer = BackendServers.getInstance();
    backendContainer.init(appDir, appEngineWebXml, address);
  }

  /**
   * Sets the properties that will be used by the local services to
   * configure themselves. This method must be called before the server
   * has been started.
   *
   * @param properties a, maybe {@code null}, set of properties.
   *
   * @throws IllegalStateException if the server has already been started.
   */
  public void setServiceProperties(Map<String,String> properties) {
    if (serverState != ServerState.INITIALIZING) {
      String msg = "Cannot set service properties after the server has been started.";
      throw new IllegalStateException(msg);
    }
    serviceProperties = properties;
    backendContainer.setServiceProperties(properties);
  }

  /**
   * Starts the server.
   *
   * @throws IllegalStateException If the server has already been started or
   * shutdown.
   * @throws AppEngineConfigException If no WEB-INF directory can be found or
   * WEB-INF/appengine-web.xml does not exist.
   */
  public void start() throws Exception {
    if (serverState != ServerState.INITIALIZING) {
      throw new IllegalStateException("Cannot start a server that has already been started.");
    }

    initializeLogging();

    ApiProxyLocalFactory factory = new ApiProxyLocalFactory();
    ApiProxyLocal localImpl = factory.create(environment);
    localImpl.setProperties(serviceProperties);
    ApiProxy.setDelegate(localImpl);

    TimeZone currentTimeZone = null;

    try {
      currentTimeZone = setServerTimeZone();
      mainContainer.startup();
      backendContainer.startupAll(mainContainer.getBackendsXml());
    } catch (BindException ex) {
      System.err.println();
      System.err.println("************************************************");
      System.err.println("Could not open the requested socket: " + ex.getMessage());
      System.err.println("Try overriding --address and/or --port.");
      System.exit(2);
    } finally {
      ApiProxy.clearEnvironmentForCurrentThread();
      restoreLocalTimeZone(currentTimeZone);
    }
    serverState = ServerState.RUNNING;

    String prettyAddress = mainContainer.getAddress();
    if (prettyAddress.equals("0.0.0.0") || prettyAddress.equals("127.0.0.1")) {
      prettyAddress = "localhost";
    }
    logger.info("The server is running at http://" + prettyAddress + ":" +
                mainContainer.getPort() + "/");
  }

  /**
   * Change the TimeZone for the current thread. By calling this method before
   * {@link ContainerService#startup()} start}, we set the default TimeZone for the
   * DevAppServer and all of its related services.
   *
   * @return the previously installed ThreadLocal TimeZone
   */
  private TimeZone setServerTimeZone() {
    String sysTimeZone = serviceProperties.get("appengine.user.timezone.impl");
    if (sysTimeZone != null && sysTimeZone.trim().length() > 0) {
      return null;
    }

    TimeZone utc = TimeZone.getTimeZone("UTC");
    assert utc.getID().equals("UTC") : "Unable to retrieve the UTC TimeZone";

    try {
      Field f = TimeZone.class.getDeclaredField("defaultZoneTL");
      f.setAccessible(true);
      ThreadLocal tl = (ThreadLocal) f.get(null);
      Method getZone = ThreadLocal.class.getMethod("get");
      TimeZone previousZone = (TimeZone) getZone.invoke(tl);
      Method setZone = ThreadLocal.class.getMethod("set", Object.class);
      setZone.invoke(tl, utc);
      return previousZone;
    } catch (Exception e) {
      throw new RuntimeException("Unable to set the TimeZone to UTC", e);
    }
  }

  /**
   * Restores the ThreadLocal TimeZone to {@code timeZone}.
   */
  private void restoreLocalTimeZone(TimeZone timeZone) {
    String sysTimeZone = serviceProperties.get("appengine.user.timezone.impl");
    if (sysTimeZone != null && sysTimeZone.trim().length() > 0) {
      return;
    }

    try {
      Field f = TimeZone.class.getDeclaredField("defaultZoneTL");
      f.setAccessible(true);
      ThreadLocal tl = (ThreadLocal) f.get(null);
      Method setZone = ThreadLocal.class.getMethod("set", Object.class);
      setZone.invoke(tl, timeZone);
    } catch (Exception e) {
      throw new RuntimeException("Unable to restore the previous TimeZone", e);
    }
  }

  @Override
  public void restart() throws Exception {
    if (serverState != ServerState.RUNNING) {
      throw new IllegalStateException("Cannot restart a server that is not currently running.");
    }
    mainContainer.shutdown();
    backendContainer.shutdownAll();
    mainContainer.startup();
    backendContainer.startupAll(mainContainer.getBackendsXml());
  }

  /**
   * Shut down the server.
   *
   * @throws IllegalStateException If the server has not been started or has
   * already been shutdown.
   */
  public void shutdown() throws Exception {
    if (serverState != ServerState.RUNNING) {
      throw new IllegalStateException("Cannot shutdown a server that is not currently running.");
    }
    mainContainer.shutdown();
    backendContainer.shutdownAll();
    ApiProxy.setDelegate(null);
    serverState = ServerState.SHUTDOWN;
  }

  /**
   * @return the servlet container listener port number.
   */
  public int getPort() {
    return mainContainer.getPort();
  }

  /**
   * Returns the web app context.  Useful in embedding scenarios to allow the
   * embedder to install servlets, etc.  Any such modification should be done
   * before calling {@link #start()}.
   *
   * @see ContainerService#getAppContext
   */
  public AppContext getAppContext() {
    return mainContainer.getAppContext();
  }

  /**
   * Reset the container EnvironmentVariableMismatchSeverity.
   */
  public void setThrowOnEnvironmentVariableMismatch(boolean throwOnMismatch) {
    mainContainer.setEnvironmentVariableMismatchSeverity(throwOnMismatch ? ERROR : WARNING);
  }

  /**
   * We're happy with the default logging behavior, which is to
   * install a {@link ConsoleHandler} at the root level.  The only
   * issue is that we want its level to be FINEST to be consistent
   * with our runtime environment.
   *
   * <p>Note that this does not mean that any fine messages will be
   * logged by default -- each Logger still defaults to INFO.
   * However, it is sufficient to call {@link Logger#setLevel(Level)}
   * to adjust the level.
   */
  private void initializeLogging() {
    for (Handler handler : Logger.getLogger("").getHandlers()) {
      if (handler instanceof ConsoleHandler) {
        handler.setLevel(Level.FINEST);
      }
    }
  }

  ServerState getServerState() {
    return serverState;
  }
}
