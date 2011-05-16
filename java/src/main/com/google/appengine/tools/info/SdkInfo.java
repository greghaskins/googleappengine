// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.info;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.io.File;

/**
 * Retrieves installation information for the App Engine SDK.
 *
 */
public class SdkInfo {

  public static final String SDK_ROOT_PROPERTY = "appengine.sdk.root";

  private static final String DEFAULT_SERVER = "appengine.google.com";

  private static boolean isInitialized = false;
  private static File sdkRoot = null;
  private static List<File> sharedLibFiles = null;
  private static List<URL> sharedLibs = null;
  private static List<File> userLibFiles = null;
  private static List<URL> userLibs = null;

  static List<URL> toURLs(List<File> files) {
    List<URL> urls = new ArrayList<URL>(files.size());
    for (File file : files) {
      urls.add(toURL(file));
    }
    return urls;
  }

  @SuppressWarnings({"deprecation"})
  static URL toURL(File file) {
    try {
      return file.toURL();
    } catch (MalformedURLException e) {
      throw new RuntimeException("Unable get a URL from " + file, e);
    }
  }

  static List<File> getLibs(File sdkRoot, String libSubDir) {
    return _getLibs(sdkRoot, libSubDir, false);
  }

  static List<File> getLibsRecursive(File sdkRoot, String libSubDir) {
    return _getLibs(sdkRoot, libSubDir, true);
  }

  static List<File> _getLibs(File sdkRoot, String libSubDir, boolean recursive) {
    File subDir = new File(sdkRoot, "lib" + File.separator + libSubDir);

    if (!subDir.exists()) {
      throw new IllegalArgumentException("Unable to find " + subDir.getAbsolutePath());
    }

    List<File> libs = new ArrayList<File>();
    getLibs(subDir, libs, recursive);
    return libs;
  }

  static void getLibs(File dir, List<File> list, boolean recursive) {
    for (File f : dir.listFiles()) {
      if (f.isDirectory() && recursive) {
        getLibs(f, list, recursive);
      } else {
        if (f.getName().endsWith(".jar")) {
          list.add(f);
        }
      }
    }
  }

  private static File findSdkRoot() {
    String explicitRootString = System.getProperty(SDK_ROOT_PROPERTY);
    if (explicitRootString != null) {
      return new File(explicitRootString);
    }

    URL codeLocation = SdkInfo.class.getProtectionDomain().getCodeSource().getLocation();
    String msg = "Unable to discover the Google App Engine SDK root. This code should be loaded " +
        "from the SDK directory, but was instead loaded from " + codeLocation + ".  Specify " +
        "-Dappengine.sdk.root to override the SDK location.";
    File libDir;
    try {
      libDir = new File(codeLocation.toURI());
    } catch (URISyntaxException e) {
      libDir = new File(codeLocation.getFile());
    }
    while (!libDir.getName().equals("lib")) {
      libDir = libDir.getParentFile();
      if (libDir == null) {
        throw new RuntimeException(msg);
      }
    }
    return libDir.getParentFile();
  }

  /**
   * Returns the full paths of all shared libraries for the SDK. Users
   * should compile against these libraries, but <b>not</b> bundle them
   * with their web application. These libraries are already included
   * as part of the App Engine runtime.
   */
  public static List<URL> getSharedLibs() {
    init();
    return sharedLibs;
  }

  /**
   * Returns the paths of all shared libraries for the SDK.
   */
  public static List<File> getSharedLibFiles() {
    init();
    return sharedLibFiles;
  }

  /**
   * Returns the full paths of all user libraries for the SDK. Users
   * should both compile against and deploy these libraries
   * in the WEB-INF/lib folder of their web applications.
   */
  public static List<URL> getUserLibs() {
    init();
    return userLibs;
  }

  /**
   * Returns the paths of all user libraries for the SDK.
   */
  public static List<File> getUserLibFiles() {
    init();
    return userLibFiles;
  }

  /**
   * Returns the path to the root of the SDK.
   */
  public static File getSdkRoot() {
    init();
    return sdkRoot;
  }

  /**
   * Explicitly specifies the path to the root of the SDK.  This takes
   * precendence over the {@code appengine.sdk.root} system property,
   * but must be called before any other methods in this class.
   *
   * @throws IllegalStateException If any other methods have already
   * been called.
   */
  public synchronized static void setSdkRoot(File root) {
    if (isInitialized) {
      throw new IllegalStateException("Cannot set SDK root after initialization has occurred.");
    }
    sdkRoot = root;
  }

  public static Version getLocalVersion() {
    return new LocalVersionFactory(getUserLibFiles()).getVersion();
  }

  public static String getDefaultServer() {
    return DEFAULT_SERVER;
  }

  private synchronized static void init() {
    if (!isInitialized) {
      if (sdkRoot == null) {
        sdkRoot = findSdkRoot();
      }
      sharedLibFiles = Collections.unmodifiableList(getLibsRecursive(sdkRoot, "shared"));
      sharedLibs = Collections.unmodifiableList(toURLs(sharedLibFiles));
      userLibFiles = Collections.unmodifiableList(getLibsRecursive(sdkRoot, "user"));
      userLibs = Collections.unmodifiableList(toURLs(userLibFiles));
      isInitialized = true;
    }
  }
}
