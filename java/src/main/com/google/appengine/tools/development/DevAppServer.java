// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import java.util.Map;

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
public interface DevAppServer {

  /**
   * {@code DevAppServer} listens on this network address for incoming
   * HTTP requests.  This can be overriden with {@code
   * --address=<addr>}.
   */
  public static final String DEFAULT_HTTP_ADDRESS = "127.0.0.1";

  /**
   * {@code DevAppServer} listens on this port for incoming HTTP
   * requests.  This can be overriden with {@code
   * --port=NNNN}.
   */
  public static final int DEFAULT_HTTP_PORT = 8080;

  /**
   * Sets the properties that will be used by the local services to
   * configure themselves. This method must be called before the server
   * has been started.
   *
   * @param properties a, maybe {@code null}, set of properties.
   *
   * @throws IllegalStateException if the server has already been started.
   */
  public void setServiceProperties(Map<String,String> properties);

  /**
   * Starts the server.
   *
   * @throws IllegalStateException If the server has already been started or
   * shutdown.
   * @throws com.google.apphosting.utils.config.AppEngineConfigException
   * If no WEB-INF directory can be found or WEB-INF/appengine-web.xml does
   * not exist.
   */
  public void start() throws Exception;

  /**
   * Restart the server to reload disk and class changes.
   *
   * @throws IllegalStateException If the server has not been started or has
   * already been shutdown.
   */
  public void restart() throws Exception;

  /**
   * Shut down the server.
   *
   * @throws IllegalStateException If the server has not been started or has
   * already been shutdown.
   */
  public void shutdown() throws Exception;

  /**
   * @return the servlet container listener port number.
   */
  public int getPort();

  /**
   * Returns the web app context.  Useful in embedding scenarios to allow the
   * embedder to install servlets, etc.  Any such modification should be done
   * before calling {@link #start()}.
   *
   * @see ContainerService#getAppContext
   */
  public AppContext getAppContext();

  /**
   * Reset the container EnvironmentVariableMismatchSeverity.
   */
  public void setThrowOnEnvironmentVariableMismatch(boolean throwOnMismatch);
}
