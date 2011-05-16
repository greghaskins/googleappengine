// Copyright 2008 Google Inc.  All rights reserved.

package com.google.appengine.tools.enhancer;

import com.google.appengine.tools.info.SdkImplInfo;
import com.google.appengine.tools.util.Logging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The command-line interface for ORM enhancing. Usage:
 * <pre>
 * java -cp classpath org.datanucleus.enhancer.DataNucleusEnhancer [options] [jdo-files] [class-files]
 * where options can be
 *   -persistenceUnit persistence-unit-name : Name of a "persistence-unit" to enhance the classes for
 *   -d target-dir-name : Write the enhanced classes to the specified directory
 *   -api api-name : Name of the API we are enhancing for (JDO, JPA). Default is JDO
 *   -enhancerName name : Name of the ClassEnhancer to use. Options ASM
 *   -checkonly : Just check the classes for enhancement status
 *   -v : verbose output
 *
 * where classpath must contain the following
 *   - your classes
 *   - your meta-data files
 * <pre>
 *
 */
public class Enhance {

  public static void main(String args[]) {
    Logging.initializeLogging();
    new Enhance(args);
  }

  public Enhance(String[] args) {
    PrintWriter writer;
    File logFile = null;

    try {
      logFile = File.createTempFile("enhance", ".log");
      writer = new PrintWriter(new FileWriter(logFile), true);
    } catch (IOException e) {
      throw new RuntimeException("Unable to enable logging.", e);
    }

    try {
      Set<URL> targets = getEnhanceTargets();
      Enhancer enhancer = new Enhancer();
      enhancer.setTargets(targets);
      enhancer.setArgs(args);
      enhancer.execute();
    } catch (Exception e) {
      System.out.println("Encountered a problem: " + e.getMessage());
      System.out.println("Please see the logs [" + logFile.getAbsolutePath() +
          "] for further information.");
      e.printStackTrace(writer);
      System.exit(1);
    }
  }

  /**
   * We assume that every URL on our classpath is an enhancer target.
   * This is ugly, but it's how DataNucleus does it and is currently
   * the path of least resistance.
   */
  private Set<URL> getEnhanceTargets() {
    URLClassLoader myLoader = (URLClassLoader) getClass().getClassLoader();
    URL[] urls = myLoader.getURLs();
    Set<URL> enhanceTargets = new HashSet<URL>(Arrays.asList(urls));
    URL toolsJar = SdkImplInfo.getToolsApiJar();
    for (URL url : enhanceTargets) {
      if (url.sameFile(toolsJar)) {
        enhanceTargets.remove(toolsJar);
        break;
      }
    }

    return enhanceTargets;
  }
}
