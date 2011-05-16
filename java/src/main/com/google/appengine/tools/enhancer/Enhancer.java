// Copyright 2008 Google Inc.  All rights reserved.

package com.google.appengine.tools.enhancer;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Set;

/**
 * Executes ORM enhancement against jars and class files.
 *
 */
public class Enhancer {

  private static final String DATANUCLEUS_ENHANCER_CLASS
      = "org.datanucleus.enhancer.DataNucleusEnhancer";

  private Set<URL> enhanceTargets;
  private String[] args;

  /**
   * Sets the class files and jar files which will be enhanced.
   *
   * @param enhanceTargets must not be null
   */
  public void setTargets(Set<URL> enhanceTargets) {
    this.enhanceTargets = enhanceTargets;
  }

  /**
   * Sets arguments to be passed to the underlying enhancer.
   *
   * @param args must not be null
   */
  public void setArgs(String[] args) {
    this.args = args;
  }

  /**
   * Runs enhancement.
   */
  public void execute() {
    if (enhanceTargets == null) {
      throw new IllegalArgumentException("Must set the targets for enhancement.");
    }

    if (args == null) {
      throw new IllegalArgumentException("Must set the enhancer arguments.");
    }

    ClassLoader enhancerLoader = new EnhancerLoader(enhanceTargets);
    Thread.currentThread().setContextClassLoader(enhancerLoader);
    try {
      Class enhancerClass = enhancerLoader.loadClass(DATANUCLEUS_ENHANCER_CLASS);
      Method main = enhancerClass.getMethod("main", String[].class);
      main.invoke(null, new Object[] {args});
    } catch (Exception e) {
      throw new RuntimeException("Unexpected exception", e);
    }
  }
}
