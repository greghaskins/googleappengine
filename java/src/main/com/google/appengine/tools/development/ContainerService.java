// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.apphosting.utils.config.BackendsXml;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;

import java.io.File;
import java.util.Map;

/**
 * Provides the backing servlet container support for the {@link DevAppServer},
 * as discovered via {@link ServiceProvider}.
 * <p>
 * More specifically, this interface encapsulates the interactions between the
 * {@link DevAppServer} and the underlying servlet container, which by default
 * uses Jetty.
 *
 */
public interface ContainerService {

  /**
   * The severity with which we'll treat environment variable mismatches.
   */
  enum EnvironmentVariableMismatchSeverity {
    WARNING,
    ERROR,
    IGNORE
  }

  /**
   * Sets up the necessary configuration parameters.
   *
   * @param devAppServerVersion Version of the devAppServer.
   * @param appDir The location of the application to run.
   * @param webXmlLocation The location of a file whose format complies with
   * http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd.  Can be null.
   * @param appEngineWebXmlReader The reader that will be used to create
   * an instance of {@link com.google.apphosting.utils.config.AppEngineWebXml}.
   * If {@code null}, an instance of {@link AppEngineWebXmlReader} will be
   * instantiated with {@code appDir} as the constructor argument.
   * @param address The address on which the server will run
   * @param port The port to which the server will be bound.  If 0, an
   * available port will be selected.
   *
   * @return A LocalServerEnvironment describing the environment in which
   * the server is running.
   */
  LocalServerEnvironment configure(String devAppServerVersion, File appDir,
      String webXmlLocation, AppEngineWebXmlReader appEngineWebXmlReader,
      String address, int port);

  /**
   * Starts up the servlet container.
   *
   * @throws Exception Any exception from the container will be rethrown as is.
   */
  void startup() throws Exception;

  /**
   * Shuts down the servlet container.
   *
   * @throws Exception Any exception from the container will be rethrown as is.
   */
  void shutdown() throws Exception;

  /**
   * Returns the listener network address, however it's decided during
   * the servlet container deployment.
   */
  String getAddress();

  /**
   * Returns the listener port number, however it's decided during the servlet
   * container deployment.
   */
  int getPort();

  /**
   * Returns the context representing the currently executing webapp.
   */
  AppContext getAppContext();

  /**
   * Return the AppEngineWebXml configuration of this container
   */
  AppEngineWebXml getAppEngineWebXmlConfig();

  BackendsXml getBackendsXml();

  /**
   * Returns the root directory of the application.
   */
  File getAppDirectory();

  /**
   * Overrides the default EnvironmentVariableMismatchSeverity setting, to
   * disable exceptions during the testing.
   *
   * @param val The new EnvironmentVariableMismatchSeverity.
   * @see EnvironmentVariableMismatchSeverity
   */
  void setEnvironmentVariableMismatchSeverity(EnvironmentVariableMismatchSeverity val);

  /**
   * Get a set of properties to be passed to each service, based on the
   * AppEngineWebXml configuration.
   *
   * @return the map of properties to be passed to each service.
   */
  Map<String, String> getServiceProperties();
}
