// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.appengine.tools.development.agent.AppEngineDevAgent;
import com.google.apphosting.utils.security.SecurityManagerInstaller;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.Permission;
import java.util.PropertyPermission;

import sun.security.util.SecurityConstants;

/**
 * Creates new {@link DevAppServer DevAppServers} which can be used to launch
 * web applications.
 *
 */
public class DevAppServerFactory {

  static final String DEV_APP_SERVER_CLASS =
      "com.google.appengine.tools.development.DevAppServerImpl";

  /**
   * Creates a new {@link DevAppServer} ready to start serving
   *
   * @param appLocation The top-level directory of the web application to be run
   * @param address Address to bind to
   * @param port Port to bind to
   *
   * @return a not {@code null} {@code DevAppServer}
   */
  public DevAppServer createDevAppServer(File appLocation, String address, int port) {
    return createDevAppServer(
        new Class[]{File.class, String.class, Integer.TYPE},
        new Object[]{appLocation, address, port});
  }

  /**
   * Creates a new {@link DevAppServer} ready to start serving.  Only exposed
   * to clients that can access it via reflection to keep it out of the public
   * api.
   *
   * @param appLocation The top-level directory of the web application to be run
   * @param appEngineWebXml The name of the app engine config file, relative to
   * the WEB-INF directory.  If {@code null},
   * {@code com.google.apphosting.utils.config.AppEngineWebXmlReader#DEFAULT_FILENAME} is used.
   * @param address Address to bind to
   * @param port Port to bind to
   * @param useCustomStreamHandler If {@code true}, install
   * {@link StreamHandlerFactory}.  This is "normal" behavior for the dev
   * app server but tests may want to disable this since there are some
   * compatibility issues with our custom handler and Selenium.
   *
   * @return a not {@code null} {@code DevAppServer}
   */
  @SuppressWarnings({"UnusedDeclaration", "unused"})
  private DevAppServer createDevAppServer(
      File appLocation, String appEngineWebXml, String address, int port,
      boolean useCustomStreamHandler) {
    return createDevAppServer(
        new Class[]{
            File.class, String.class, String.class, String.class, Integer.TYPE, Boolean.TYPE},
        new Object[]{appLocation, null, appEngineWebXml, address, port, useCustomStreamHandler});
  }

  private DevAppServer createDevAppServer(Class[] ctorArgTypes, Object[] ctorArgs) {
    SecurityManagerInstaller.install();

    DevAppServerClassLoader loader = DevAppServerClassLoader.newClassLoader(
        DevAppServerFactory.class.getClassLoader());

    testAgentIsInstalled();

    DevAppServer devAppServer;
    try {
      Class<?> devAppServerClass = Class.forName(DEV_APP_SERVER_CLASS, true, loader);
      Constructor cons = devAppServerClass.getConstructor(ctorArgTypes);
      cons.setAccessible(true);
      devAppServer = (DevAppServer) cons.newInstance(ctorArgs);
    } catch (Exception e) {
      Throwable t = e;
      if (e instanceof InvocationTargetException) {
        t = e.getCause();
      }
      throw new RuntimeException("Unable to create a DevAppServer", t);
    }
    System.setSecurityManager(new CustomSecurityManager(devAppServer));
    return devAppServer;
  }

  private void testAgentIsInstalled() {
    try {
      AppEngineDevAgent.getAgent();
    } catch (Throwable t) {
      String msg = "Unable to locate the App Engine agent. Please use dev_appserver, KickStart, "
          + " or set the jvm flag: \"-javaagent:<sdk_root>/lib/agent/appengine-agent.jar\"";
      throw new RuntimeException(msg, t);
    }
  }

  /**
   * Implements custom security behavior. This SecurityManager only applies
   * checks when code is running in the context of a DevAppServer thread
   * handling an http request.
   */
  private static class CustomSecurityManager extends SecurityManager {

    private static final RuntimePermission PERMISSION_MODIFY_THREAD_GROUP =
        new RuntimePermission("modifyThreadGroup");

    private static final RuntimePermission PERMISSION_MODIFY_THREAD =
        new RuntimePermission("modifyThread");

    private static final String KEYCHAIN_JNILIB = "/libkeychain.jnilib";

    private static final Object PERMISSION_LOCK = new Object();

    private final DevAppServer devAppServer;

    public CustomSecurityManager(DevAppServer devAppServer) {
      this.devAppServer = devAppServer;
    }

    private synchronized boolean appHasPermission(Permission perm) {
      synchronized (PERMISSION_LOCK) {
        AppContext context = devAppServer.getAppContext();
        if (context.getUserPermissions().implies(perm) ||
            context.getApplicationPermissions().implies(perm)) {
          return true;
        }
      }

      return SecurityConstants.FILE_READ_ACTION.equals(perm.getActions()) &&
          perm.getName().endsWith(KEYCHAIN_JNILIB);
    }

    @Override
    public void checkPermission(Permission perm) {
      if (perm instanceof PropertyPermission) {
        return;
      }

      if (isDevAppServerThread()) {
        if (appHasPermission(perm)) {
            return;
        }

        super.checkPermission(perm);
      }
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
      if (isDevAppServerThread()) {
        if (appHasPermission(perm)) {
          return;
        }
        super.checkPermission(perm, context);
      }
    }

    /**
     * Don't allow user code permission to muck with Threads.
     * Normally the JDK only enforces this for the root ThreadGroup, but
     * we enforce it at all times.
     */
    @Override
    public void checkAccess(ThreadGroup g) {
      if (g == null) {
        throw new NullPointerException("thread group can't be null");
      }

      checkPermission(PERMISSION_MODIFY_THREAD_GROUP);
    }

    /**
     * Enforces the same thread policy as {@link #checkAccess(ThreadGroup)}.
     */
    @Override
    public void checkAccess(Thread t) {
      if (t == null) {
        throw new NullPointerException("thread can't be null");
      }

      checkPermission(PERMISSION_MODIFY_THREAD);
    }

    public boolean isDevAppServerThread() {
      return Boolean.getBoolean("devappserver-thread-" + Thread.currentThread().getName());
    }
  }
}
