// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.appengine.api.utils.SystemProperty;
import com.google.appengine.tools.info.SdkImplInfo;
import com.google.appengine.tools.info.SdkInfo;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;
import com.google.apphosting.utils.config.BackendsXml;
import com.google.apphosting.utils.config.BackendsXmlReader;
import com.google.apphosting.utils.config.AppYaml;
import com.google.apphosting.utils.config.WebXml;
import com.google.apphosting.utils.config.WebXmlReader;
import com.google.common.base.Join;
import com.google.common.collect.Maps;

import java.beans.PropertyChangeSupport;
import java.lang.reflect.Field;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Permissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Common implementation for the {@link ContainerService} interface.
 *
 * There should be no reference to any third-party servlet container from here.
 *
 */
public abstract class AbstractContainerService implements ContainerService {

  private static final Logger log = Logger.getLogger(AbstractContainerService.class.getName());

  protected static final String _AH_URL_RELOAD = "/_ah/reloadwebapp";

  private static final String LOGGING_CONFIG_FILE = "java.util.logging.config.file";

  /**
   * The network address on which the server is listening for http requests.
   */
  protected String address;

  /**
   * The port on which the server is listening for http requests.
   */
  protected int port;

  /**
   * The location of web.xml.  If not provided, defaults to
   * <appDir>/WEB-INF/web.xml
   */
  protected String webXmlLocation;

  protected File appDir;
  protected String devAppServerVersion;

  /**
   * The reader to use to get an {@link AppEngineWebXml}
   * instance. If not set, the instance will be constructed from
   * <appDir>/WEB-INF/appengine-web.xml.
   */
  protected AppEngineWebXmlReader appEngineWebXmlReader;

  protected AppEngineWebXml appEngineWebXml;

  protected WebXml webXml;

  protected BackendsXml backendsXml;

  /**
   * We modify the system properties when the dev appserver is
   * launched using key/value pairs defined in appengine-web.xml.
   * This can make it very easy to leak state across tests that launch
   * instances of the dev appserver, so we use this member to keep
   * track of the original values all system properties at start-up.
   * We then restore the values when we shutdown the server.
   */
  private final Properties originalSysProps = new Properties();

  /**
   * The severity of an environment variable mismatch.
   */
  private EnvironmentVariableMismatchSeverity envVarMismatchSeverity =
      EnvironmentVariableMismatchSeverity.ERROR;

  /**
   * Latch that will open once the server is fully initialized.
   */
  private CountDownLatch serverInitLatch;

  public final LocalServerEnvironment configure(String devAppServerVersion, final File appDir,
      String webXmlLocation, AppEngineWebXmlReader appEngineWebXmlReader,
      final String address, int port) {
    this.appDir = appDir;
    this.devAppServerVersion = devAppServerVersion;
    this.webXmlLocation = webXmlLocation;
    this.appEngineWebXmlReader = appEngineWebXmlReader;
    this.address = address;
    this.port = port;
    this.serverInitLatch = new CountDownLatch(1);
    return new LocalServerEnvironment() {
      public File getAppDir() {
        return appDir;
      }

      public String getAddress() {
        return address;
      }

      public int getPort() {
        return AbstractContainerService.this.port;
      }

      public void waitForServerToStart() throws InterruptedException {
        serverInitLatch.await();
      }

      public boolean enforceApiDeadlines() {
        return !Boolean.getBoolean("com.google.appengine.disable_api_deadlines");
      }
    };
  }

  /**
   * This is made final, and detail implementation (that is specific to any
   * particular servlet container) goes to individual "template" methods.
   */
  public final void startup() throws Exception {
    File webAppDir = initContext();
    loadAppEngineWebXml(webAppDir);

    startContainer();
    startHotDeployScanner();
    serverInitLatch.countDown();
  }

  public final void shutdown() throws Exception {
    stopHotDeployScanner();
    stopContainer();
    restoreSystemProperties();
  }

  /**
   * Set up the webapp context in a container specific way.
   *
   * @return the effective webapp directory.
   */
  protected abstract File initContext() throws IOException;

  /**
   * Start up the servlet container runtime.
   */
  protected abstract void startContainer() throws Exception;

  /**
   * Stop the servlet container runtime.
   */
  protected abstract void stopContainer() throws Exception;

  /**
   * Start up the hot-deployment scanner.
   */
  protected abstract void startHotDeployScanner() throws Exception;

  /**
   * Stop the hot-deployment scanner.
   */
  protected abstract void stopHotDeployScanner() throws Exception;

  /**
   * Re-deploy the current webapp context in a container specific way,
   * while taking into account possible appengine-web.xml change too,
   * without restarting the server.
   */
  protected abstract void reloadWebApp() throws Exception;

  @Override
  public String getAddress() {
    return address;
  }

  @Override
  public AppEngineWebXml getAppEngineWebXmlConfig(){
    return appEngineWebXml;
  }

  @Override
  public BackendsXml getBackendsXml() {
    return backendsXml;
  }

  @Override
  public File getAppDirectory() {
    return appDir;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public void setEnvironmentVariableMismatchSeverity(EnvironmentVariableMismatchSeverity val) {
    envVarMismatchSeverity = val;
  }

  protected Permissions getUserPermissions() {
    return appEngineWebXml.getUserPermissions();
  }

  protected void loadAppEngineWebXml(File webAppDir) {
    if (appEngineWebXmlReader == null) {
      appEngineWebXmlReader =
          new AppEngineWebXmlReader(webAppDir.getAbsolutePath());
    }

    AppYaml.convert(new File(appDir, "WEB-INF"),
                    appEngineWebXmlReader.getFilename(),
                    (webXmlLocation == null ?
                     new File(appDir, "WEB-INF/web.xml").getAbsolutePath() :
                     webXmlLocation));
    appEngineWebXml = appEngineWebXmlReader.readAppEngineWebXml();
    if (appEngineWebXml.getAppId() == null || appEngineWebXml.getAppId().length() == 0) {
      appEngineWebXml.setAppId("no_app_id");
    }
    webXml = new WebXmlReader(webAppDir.getAbsolutePath()).readWebXml();
    setSystemProperties(appEngineWebXml);
    checkEnvironmentVariables(appEngineWebXml);
    updateLoggingConfiguration(originalSysProps, appEngineWebXml.getSystemProperties());
    webXml.validate();

    backendsXml = new BackendsXmlReader(webAppDir.getAbsolutePath()).readBackendsXml();

    ApiProxy.setEnvironmentForCurrentThread(new LocalInitializationEnvironment(appEngineWebXml));
  }

  protected void restoreSystemProperties() {
    for (String key : appEngineWebXml.getSystemProperties().keySet()) {
      System.clearProperty(key);
    }
    System.getProperties().putAll(originalSysProps);
  }

  /** Returns {@code true} if appengine-web.xml <sessions-enabled> is true. */
  protected boolean isSessionsEnabled() {
    return appEngineWebXml.getSessionsEnabled();
  }

  /**
   * Gets all of the URLs that should be added to the classpath for an
   * application located at {@code root}.
   */
  protected URL[] getClassPathForApp(File root) {
    List<URL> appUrls = new ArrayList<URL>();

    appUrls.addAll(SdkImplInfo.getAgentRuntimeLibs());

    try {
      File classes = new File(new File(root, "WEB-INF"), "classes");
      if (classes.exists()) {
        appUrls.add(classes.toURL());
      }
    } catch (MalformedURLException ex) {
      log.log(Level.WARNING, "Could not add WEB-INF/classes", ex);
    }

    File libDir = new File(new File(root, "WEB-INF"), "lib");
    if (libDir.isDirectory()) {
      for (File file : libDir.listFiles()) {
        try {
          appUrls.add(file.toURL());
        } catch (MalformedURLException ex) {
          log.log(Level.WARNING, "Could not get URL for file: " + file, ex);
        }
      }
    }
    for (URL url : SdkImplInfo.getUserJspLibs()) {
      appUrls.add(url);
    }
    return appUrls.toArray(new URL[appUrls.size()]);
  }

  /**
   * Sets system properties that are defined in {@link AppEngineWebXml}.
   */
  protected void setSystemProperties(AppEngineWebXml appEngineWebXml) {
    SystemProperty.environment.set(SystemProperty.Environment.Value.Development);
    String release = SdkInfo.getLocalVersion().getRelease();
    if (release == null) {
      release = "null";
    }
    SystemProperty.version.set(release);
    SystemProperty.applicationId.set(appEngineWebXml.getAppId());
    SystemProperty.applicationVersion.set(appEngineWebXml.getMajorVersionId() + ".1");

    originalSysProps.putAll(System.getProperties());
    System.getProperties().putAll(appEngineWebXml.getSystemProperties());
  }

  /**
   * Updates the JVM's logging configuration to include both the
   * user's custom logging configuration and the SDK's internal
   * logging configuration.
   *
   * @param systemProperties
   * @param userSystemProperties
   */
  protected void updateLoggingConfiguration(Properties systemProperties,
      Map<String, String> userSystemProperties) {
    String userConfigFile = userSystemProperties.get(LOGGING_CONFIG_FILE);

    Properties userProperties = loadPropertiesFile(userConfigFile);
    String sdkConfigFile = systemProperties.getProperty(LOGGING_CONFIG_FILE);
    Properties sdkProperties = loadPropertiesFile(sdkConfigFile);
    Properties allProperties = new Properties();
    if (sdkProperties != null) {
      allProperties.putAll(sdkProperties);
    }
    if (userProperties != null) {
      allProperties.putAll(userProperties);
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();

    try {
      allProperties.store(out, null);
      LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(out.toByteArray()));
      fireLogManagerChangeListener();

      Logger root = Logger.getLogger("");
      Handler[] handlers = root.getHandlers();
      if (handlers != null) {
        for (Handler handler : handlers) {
          handler.setLevel(Level.FINEST);
        }
      }
    } catch (IOException e) {
      log.log(Level.WARNING, "Unable to configure logging properties.", e);
    }
  }

  private void fireLogManagerChangeListener() {
    try {
      Field field = LogManager.class.getDeclaredField("changes");
      field.setAccessible(true);
      PropertyChangeSupport support = (PropertyChangeSupport) field.get(LogManager.getLogManager());
      support.firePropertyChange(null, null, null);
    } catch (Exception ex) {
      log.log(Level.FINE, "Failed to explicitly fire LogManager PropertyChangeSupport.", ex);
    }
  }

  private Properties loadPropertiesFile(String file) {
    if (file == null) {
      return null;
    }
    file = file.replace('/', File.separatorChar);
    File f = new File(file);
    if (!f.isAbsolute()) {
      f = new File(appDir + File.separator + f.getPath());
    }
    InputStream inputStream = null;
    try {
      inputStream = new BufferedInputStream(new FileInputStream(f));
      Properties props = new Properties();
      props.load(inputStream);
      return props;
    } catch (IOException e) {
      log.log(Level.WARNING, "Unable to load properties file, " + f.getAbsolutePath(), e);
      return null;
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
        }
      }
    }
  }

  protected void checkEnvironmentVariables(AppEngineWebXml appEngineWebXml) {
    Map<String, String> missingEnvEntries = Maps.newHashMap();
    for (Map.Entry<String, String> entry : appEngineWebXml.getEnvironmentVariables().entrySet()) {
      if (!entry.getValue().equals(System.getenv(entry.getKey()))) {
        missingEnvEntries.put(entry.getKey(), entry.getValue());
      }
    }
    if (!missingEnvEntries.isEmpty()) {
      String msg =
          "One or more environment variables have been configured in appengine-web.xml that have "
          + "missing or different values in your local environment. We recommend you use system "
          + "properties instead, but if you are interacting with legacy code that requires "
          + "specific environment variables to have specific values, please set these environment "
          + "variables in your environment before running.\n"
          + Join.join("\n", missingEnvEntries);

      if (envVarMismatchSeverity == EnvironmentVariableMismatchSeverity.WARNING) {
        log.warning(msg);
      } else if (envVarMismatchSeverity == EnvironmentVariableMismatchSeverity.ERROR) {
        throw new IncorrectEnvironmentVariableException(msg, missingEnvEntries);
      }
    }
  }

  static class IncorrectEnvironmentVariableException extends
      AppEngineConfigException {

    private final Map<String, String> missingEnvEntries;
    private IncorrectEnvironmentVariableException(String msg,
        Map<String, String> missingEnvEntries) {
      super(msg);
      this.missingEnvEntries = missingEnvEntries;
    }

    public Map<String, String> getMissingEnvEntries() {
      return missingEnvEntries;
    }
  }

  /**
   * A fake {@link LocalEnvironment} implementation that is used during the
   * initialization of the Development AppServer.
   */
  protected static class LocalInitializationEnvironment extends LocalEnvironment {
    public LocalInitializationEnvironment(AppEngineWebXml appEngineWebXml) {
      super(appEngineWebXml);
    }

    public String getEmail() {
      return null;
    }

    public boolean isLoggedIn() {
      return false;
    }

    public boolean isAdmin() {
      return false;
    }
  }
}
