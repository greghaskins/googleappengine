// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.jetty.DevAppEngineWebAppContext;
import com.google.apphosting.utils.jetty.JettyLogger;
import com.google.apphosting.utils.jetty.StubSessionManager;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.HandlerWrapper;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.resource.Resource;
import org.mortbay.util.Scanner;

import java.io.File;
import java.io.IOException;
import java.io.FilenameFilter;
import java.net.URL;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.Semaphore;
import java.security.Permissions;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Implements a Jetty backed {@link ContainerService}.
 *
 */
@ServiceProvider(ContainerService.class)
public class JettyContainerService extends AbstractContainerService {

  private static final Logger log = Logger.getLogger(JettyContainerService.class.getName());

  public final static String WEB_DEFAULTS_XML =
      "com/google/appengine/tools/development/webdefault.xml";

  private static final int MAX_SIMULTANEOUS_API_CALLS = 100;

  /**
   * Specify which {@link Configuration} objects should be invoked when
   * configuring a web application.
   *
   * <p>This is a subset of:
   *   org.mortbay.jetty.webapp.WebAppContext.__dftConfigurationClasses
   *
   * <p>Specifically, we've removed {@link JettyWebXmlConfiguration} which allows
   * users to use {@code jetty-web.xml} files.
   */
  private static final String CONFIG_CLASSES[] = new String[] {
        "org.mortbay.jetty.webapp.WebXmlConfiguration",
        "org.mortbay.jetty.webapp.TagLibConfiguration"
  };

  private static final String WEB_XML_ATTR =
      "com.google.appengine.tools.development.webXml";
  private static final String APPENGINE_WEB_XML_ATTR =
      "com.google.appengine.tools.development.appEngineWebXml";

  static {
    System.setProperty("org.mortbay.log.class", JettyLogger.class.getName());
  }

  private final static int SCAN_INTERVAL_SECONDS = 5;

  /**
   * Jetty webapp context.
   */
  private WebAppContext context;

  /**
   * Our webapp context.
   */
  private AppContext appContext;

  /**
   * The Jetty server.
   */
  private Server server;

  /**
   * Hot deployment support.
   */
  private Scanner scanner;

  private class JettyAppContext implements AppContext {
    @Override
    public IsolatedAppClassLoader getClassLoader() {
      return (IsolatedAppClassLoader) context.getClassLoader();
    }

    @Override
    public Permissions getUserPermissions() {
      return JettyContainerService.this.getUserPermissions();
    }

    @Override
    public Permissions getApplicationPermissions() {
      return getClassLoader().getAppPermissions();
    }

    @Override
    public Object getContainerContext() {
      return context;
    }
  }

  public JettyContainerService() {
  }

  @Override
  protected File initContext() throws IOException {
    this.context = new DevAppEngineWebAppContext(appDir, devAppServerVersion);
    this.appContext = new JettyAppContext();

    context.setDescriptor(webXmlLocation);

    context.setDefaultsDescriptor(WEB_DEFAULTS_XML);

    context.setConfigurationClasses(CONFIG_CLASSES);

    File appRoot = determineAppRoot();
    URL[] classPath = getClassPathForApp(appRoot);
    context.setClassLoader(new IsolatedAppClassLoader(appRoot, classPath,
        JettyContainerService.class.getClassLoader()));

    return appRoot;
  }

  @Override
  protected void startContainer() throws Exception {
    context.setAttribute(WEB_XML_ATTR, webXml);
    context.setAttribute(APPENGINE_WEB_XML_ATTR, appEngineWebXml);

    Thread currentThread = Thread.currentThread();
    ClassLoader previousCcl = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(null);

    try {
      ApiProxyHandler apiHandler = new ApiProxyHandler(appEngineWebXml);
      apiHandler.setHandler(context);

      SelectChannelConnector connector = new SelectChannelConnector();
      connector.setHost(address);
      connector.setPort(port);
      connector.setSoLingerTime(0);

      server = new Server();
      server.addConnector(connector);
      server.setHandler(apiHandler);
      if (!isSessionsEnabled()) {
        context.getSessionHandler().setSessionManager(new StubSessionManager());
      }
      server.start();
      port = connector.getLocalPort();
    } finally {
      currentThread.setContextClassLoader(previousCcl);
    }
  }

  @Override
  protected void stopContainer() throws Exception {
    server.stop();
  }

  /**
   * Unlike the actual Jetty hot deployment support, we monitor the webapp war file or the
   * appengine-web.xml in case of a pre-exploded webapp directory, and reload the webapp whenever an
   * update is detected, i.e. a newer timestamp for the monitored file. As a single-context
   * deployment, add/delete is not applicable here.
   *
   * appengine-web.xml will be reloaded too. However, changes that require a server restart, e.g.
   * address/port, will not be part of the reload.
   */
  @Override
  protected void startHotDeployScanner() throws Exception {
    scanner = new Scanner();
    scanner.setScanInterval(SCAN_INTERVAL_SECONDS);
    scanner.setScanDir(getScanTarget());
    scanner.setFilenameFilter(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        try {
          if (name.equals(getScanTarget().getName())) {
            return true;
          }
          return false;
        }
        catch (Exception e) {
          return false;
        }
      }
    });
    scanner.scan();
    scanner.addListener(new ScannerListener());
    scanner.start();
  }

  @Override
  protected void stopHotDeployScanner() throws Exception {
    if (scanner != null) {
      scanner.stop();
    }
    scanner = null;
  }

  private class ScannerListener implements Scanner.DiscreteListener {
    @Override
    public void fileAdded(String filename) throws Exception {
      fileChanged(filename);
    }

    @Override
    public void fileChanged(String filename) throws Exception {
      log.info(filename + " updated, reloading the webapp!");
      reloadWebApp();
    }

    @Override
    public void fileRemoved(String filename) throws Exception {
    }
  }

  /**
   * To minimize the overhead, we point the scanner right to the single file in question.
   */
  private File getScanTarget() throws Exception {
    if (appDir.isFile()) {
      return appDir;
    } else {
      return new File(context.getWebInf().getFile().getPath()
          + File.separator + "appengine-web.xml");
    }
  }

  /**
   * Assuming Jetty handles race condition nicely, as this is how Jetty handles a hot deploy too.
   */
  @Override
  protected void reloadWebApp() throws Exception {
    server.getHandler().stop();
    restoreSystemProperties();

    /** same as what's in startContainer, we need suppress the ContextClassLoader here. */
    Thread currentThread = Thread.currentThread();
    ClassLoader previousCcl = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(null);
    try {
      File webAppDir = initContext();
      loadAppEngineWebXml(webAppDir);

      if (!isSessionsEnabled()) {
        context.getSessionHandler().setSessionManager(new StubSessionManager());
      }
      context.setAttribute(WEB_XML_ATTR, webXml);
      context.setAttribute(APPENGINE_WEB_XML_ATTR, appEngineWebXml);

      ApiProxyHandler apiHandler = new ApiProxyHandler(appEngineWebXml);
      apiHandler.setHandler(context);
      server.setHandler(apiHandler);

      apiHandler.start();
    } finally {
      currentThread.setContextClassLoader(previousCcl);
    }
  }

  @Override
  public AppContext getAppContext() {
    return appContext;
  }

  private File determineAppRoot() throws IOException {
    Resource webInf = context.getWebInf();
    if (webInf == null) {
      throw new AppEngineConfigException("Supplied application has to contain WEB-INF directory.");
    }
    return webInf.getFile().getParentFile();
  }

  /**
   * {@code ApiProxyHandler} wraps around an existing {@link Handler}
   * and surrounds each top-level request (i.e. not includes or
   * forwards) with a try finally block that maintains the {@link
   * ApiProxy.Environment} {@link ThreadLocal}.
   */
  private class ApiProxyHandler extends HandlerWrapper {
    private final AppEngineWebXml appEngineWebXml;

    public ApiProxyHandler(AppEngineWebXml appEngineWebXml) {
      this.appEngineWebXml = appEngineWebXml;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(String target,
                       HttpServletRequest request,
                       HttpServletResponse response,
                       int dispatch) throws IOException, ServletException {
      if (dispatch == REQUEST) {
        Semaphore semaphore = new Semaphore(MAX_SIMULTANEOUS_API_CALLS);

        LocalEnvironment env = new LocalHttpRequestEnvironment(appEngineWebXml, request);
        env.getAttributes().put(LocalEnvironment.API_CALL_SEMAPHORE, semaphore);
        ApiProxy.setEnvironmentForCurrentThread(env);
        try {
          super.handle(target, request, response, dispatch);
          if (request.getRequestURI().startsWith(_AH_URL_RELOAD)) {
            try {
              reloadWebApp();
              log.info("Reloaded the webapp context: " + request.getParameter("info"));
            } catch (Exception ex) {
              log.log(Level.WARNING, "Failed to reload the current webapp context.", ex);
            }
          }
        } finally {
          try {
            semaphore.acquire(MAX_SIMULTANEOUS_API_CALLS);
          } catch (InterruptedException ex) {
            log.log(Level.WARNING, "Interrupted while waiting for API calls to complete:", ex);
          }
          Set<RequestEndListener> listeners = (Set<RequestEndListener>) env.getAttributes().get(
              LocalEnvironment.REQUEST_END_LISTENERS);
          for (RequestEndListener listener : listeners) {
            try {
              listener.onRequestEnd(env);
            } catch (Exception ex) {
              log.log(Level.WARNING,
                  "Exception while attempting to invoke RequestEndListener " + listener.getClass()
                      + ": ", ex);
            }
          }
          ApiProxy.clearEnvironmentForCurrentThread();
        }
      } else {
        super.handle(target, request, response, dispatch);
      }
    }
  }
}
