// Copyright 2009 Google Inc. All Rights Reserved.
package com.google.appengine.tools.development.gwt;

import com.google.appengine.tools.info.SdkInfo;
import com.google.appengine.tools.info.UpdateCheck;
import com.google.appengine.tools.development.DevAppServer;
import com.google.appengine.tools.development.DevAppServerFactory;
import com.google.appengine.tools.util.Logging;
import com.google.gwt.core.ext.ServletContainer;
import com.google.gwt.core.ext.ServletContainerLauncher;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A GWT SCL that allows DevAppServer to be embedded within GWT hosted mode.
 *
 */
public class AppEngineLauncher extends ServletContainerLauncher {

  /**
   * Instead of the default address of 127.0.0.1, we use this address
   * to bind to all interfaces.  This is what most IDE-users will
   * expect.
   */
  private static final String ADDRESS = "0.0.0.0";

  private static class AppEngineServletContainer extends ServletContainer {
    private final TreeLogger logger;
    private final DevAppServer server;
    private final LogAdapterHandler logAdapter;

    public AppEngineServletContainer(TreeLogger logger, DevAppServer server,
                                     LogAdapterHandler logAdapter) {
      this.logger = logger;
      this.server = server;
      this.logAdapter = logAdapter;
    }

    @Override
    public int getPort() {
      return server.getPort();
    }

    @Override
    public void refresh() throws UnableToCompleteException {
      TreeLogger branch = logger.branch(TreeLogger.INFO, "Reloading AppEngine server");
      try {
        server.restart();
      } catch (Exception e) {
        branch.log(TreeLogger.ERROR, "Unable to reload AppEngine server", e);
        throw new UnableToCompleteException();
      }
      branch.log(TreeLogger.INFO, "Reload completed successfully");
    }

    @Override
    public void stop() throws UnableToCompleteException {
      TreeLogger branch = logger.branch(TreeLogger.INFO, "Stopping AppEngine server");
      try {
        server.shutdown();
      } catch (Exception e) {
        branch.log(TreeLogger.ERROR, "Unable to stop AppEngine server", e);
        throw new UnableToCompleteException();
      }
      branch.log(TreeLogger.INFO, "Stopped successfully");
      logAdapter.uninstall();
    }
  }

  @Override
  public ServletContainer start(TreeLogger logger, int port, File appRootDir)
      throws UnableToCompleteException {
    LogAdapterHandler logAdapter = new LogAdapterHandler(logger);
    logAdapter.install();
    Logging.initializeLogging();
    logAdapter.adjustRootHandlers();
    checkStartParams(logger, port, appRootDir);

    TreeLogger branch = logger.branch(TreeLogger.INFO, "Initializing AppEngine server");
    maybePerformUpdateCheck(branch);

    DevAppServer server = new DevAppServerFactory().createDevAppServer(
        appRootDir, ADDRESS, port);

    server.setThrowOnEnvironmentVariableMismatch(false);

    @SuppressWarnings("rawtypes")
    Map properties = System.getProperties();
    @SuppressWarnings("unchecked")
    Map<String, String> stringProperties = properties;
    server.setServiceProperties(stringProperties);

    try {
      server.start();
      return new AppEngineServletContainer(logger, server, logAdapter);
    } catch (Exception e) {
      branch.log(TreeLogger.ERROR, "Unable to start AppEngine server", e);
      throw new UnableToCompleteException();
    }
  }

  protected void maybePerformUpdateCheck(TreeLogger logger) {
    UpdateCheck updateCheck = new UpdateCheck(SdkInfo.getDefaultServer());
    if (updateCheck.allowedToCheckForUpdates()) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      if (updateCheck.maybePrintNagScreen(new PrintStream(baos))) {
        logger.log(TreeLogger.WARN, new String(baos.toByteArray()));
      }
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    if (updateCheck.checkJavaVersion(new PrintStream(baos))) {
      logger.log(TreeLogger.WARN, new String(baos.toByteArray()));
    }
  }

  private void checkStartParams(TreeLogger logger, int port, File appRootDir) {
    if (logger == null) {
      throw new NullPointerException("logger cannot be null");
    }

    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("port must be either 0 (for auto) or less than 65536");
    }

    if (appRootDir == null) {
      throw new NullPointerException("app root directory cannot be null");
    }
  }

  private static class LogAdapterHandler extends Handler implements PropertyChangeListener {
    private final TreeLogger treeLogger;

    public LogAdapterHandler(TreeLogger treeLogger) {
      this.treeLogger = treeLogger;
      setLevel(Level.FINEST);
      setFilter(null);
      setFormatter(new Formatter() {
          @Override
          public String format(LogRecord record) {
            return formatMessage(record);
          }
      });
    }

    public void install() {
      adjustRootHandlers();
      LogManager.getLogManager().addPropertyChangeListener(this);
    }

    /**
     * This method is invoked when the LogManager configuration changes.
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {
      adjustRootHandlers();
    }

    void adjustRootHandlers() {
      Logger root = Logger.getLogger("");
      boolean foundOurHandler = false;
      for (Handler handler : new ArrayList<Handler>(Arrays.asList(root.getHandlers()))) {
        if (handler == this) {
          foundOurHandler = true;
        } else if (handler instanceof ConsoleHandler) {
          root.removeHandler(handler);
        }
      }
      if (!foundOurHandler) {
        root.addHandler(this);
      }
    }

    public void uninstall() {
      LogManager.getLogManager().removePropertyChangeListener(this);
      Logger.getLogger("").removeHandler(this);
    }

    @Override
    public synchronized void publish(LogRecord record) {
      if (!isLoggable(record)) {
        return;
      }
      TreeLogger.Type type = convertLogLevel(record.getLevel());
      if (!treeLogger.isLoggable(type)) {
        return;
      }

      String message;
      try {
        message = getFormatter().format(record);
      } catch (Exception ex) {
        reportError(null, ex, ErrorManager.FORMAT_FAILURE);
        return;
      }
      treeLogger.log(type, message, record.getThrown());
    }

    private TreeLogger.Type convertLogLevel(Level level) {
      long intLevel = level.intValue();

      if (intLevel >= Level.SEVERE.intValue()) {
        return TreeLogger.Type.ERROR;
      } else if (intLevel >= Level.WARNING.intValue()) {
        return TreeLogger.Type.WARN;
      } else if (intLevel >= Level.INFO.intValue()) {
        return TreeLogger.Type.INFO;
      } else if (intLevel >= Level.FINE.intValue()) {
        return TreeLogger.Type.TRACE;
      } else if (intLevel >= Level.FINER.intValue()) {
        return TreeLogger.Type.DEBUG;
      } else {
        return TreeLogger.Type.ALL;
      }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
      flush();
    }
  }
}
