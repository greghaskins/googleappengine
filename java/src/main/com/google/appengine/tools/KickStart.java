// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools;

import com.google.appengine.tools.admin.OutputPump;
import com.google.appengine.tools.development.DevAppServerMain;
import com.google.appengine.tools.info.SdkInfo;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Launches a process in an operating-system agnostic way. Helps us avoid
 * idiosyncracies in scripts for different platforms. Currently this only
 * works for DevAppServerMain.
 *
 * Takes a command line invocation like:
 *
 * <pre>
 * java -cp ../lib/appengine-tools-api.jar com.google.appengine.tools.KickStart \
 *   --jvm_flag="-Dlog4j.configuration=log4j.props"
 *   com.google.appengine.tools.development.DevAppServerMain \
 *   --jvm_flag="-agentlib:jdwp=transport=dt_socket,server=y,address=7000"
 *   --address=localhost --port=5005 appDir
 * </pre>
 *
 * and turns it into:
 *
 * <pre>
 * java -cp &lt;an_absolute_path&gt;/lib/appengine-tools-api.jar \
 *   -Dlog4j.configuration=log4j.props \
 *   -agentlib:jdwp=transport=dt_socket,server=y,address=7000 \
 *   com.google.appengine.tools.development.DevAppServerMain \
 *   --address=localhost --port=5005 &lt;an_absolute_path&gt;/appDir
 * </pre>
 *
 * while also setting its working directory (if appropriate).
 * <p>
 * All arguments between {@code com.google.appengine.tools.KickStart} and
 * {@code com.google.appengine.tools.development.DevAppServerMain}, as well as
 * all {@code --jvm_flag} arguments after {@code DevAppServerMain}, are consumed
 * by KickStart. The remaining options after {@code DevAppServerMain} are
 * given as arguments to DevAppServerMain, without interpretation by
 * KickStart.
 *
 * At present, the only valid option to KickStart itself is:
 * <DL>
 * <DT>--jvm_flag=&lt;vm_arg&gt;</DT><DD>Passes &lt;vm_arg&gt; as a JVM
 * argument for the child JVM.  May be repeated.</DD>
 * </DL>
 *
 */
public class KickStart {

  private static final Logger logger = Logger.getLogger(KickStart.class.getName());

  private Process serverProcess = null;

  public static void main(String[] args) {
    new KickStart(args);
  }

  private KickStart(String[] args) {
    String entryClass = null;

    ProcessBuilder builder = new ProcessBuilder();
    String home = System.getProperty("java.home");
    String javaExe = home + File.separator + "bin" + File.separator + "java";

    List<String> jvmArgs = new ArrayList<String>();
    ArrayList<String> appServerArgs = new ArrayList<String>();
    List<String> command = builder.command();
    command.add(javaExe);

    boolean startOnFirstThread = System.getProperty("os.name").equalsIgnoreCase("Mac OS X");

    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith("--jvm_flag")) {
        int indexOfSplit = args[i].indexOf('=');
        if (indexOfSplit == -1) {
          String msg = "--jvm_flag syntax is --jvm_flag=<flag>\n" +
                       "--jvm_flag may be repeated to supply multiple flags";
          throw new IllegalArgumentException(msg);
        }
        String jvmArg = args[i].substring(indexOfSplit + 1);
        jvmArgs.add(jvmArg);
      } else if (args[i].startsWith("--startOnFirstThread=")) {
        String arg = args[i].substring(args[i].indexOf('=') + 1);
        startOnFirstThread = Boolean.valueOf(arg);
      } else if (entryClass == null) {
        if (args[i].charAt(0) == '-') {
          throw new IllegalArgumentException("Only --jvm_flag may precede classname, not "
              + args[i]);
        } else {
          entryClass = args[i];
          if (!entryClass.equals(DevAppServerMain.class.getName())) {
            throw new IllegalArgumentException("KickStart only works for DevAppServerMain");
          }
        }
      } else {
        appServerArgs.add(args[i]);
      }
    }

    if (entryClass == null) {
      throw new IllegalArgumentException("missing entry classname");
    }

    File newWorkingDir = newWorkingDir(args);
    builder.directory(newWorkingDir);

    if (startOnFirstThread) {
      jvmArgs.add("-XstartOnFirstThread");
    }

    String classpath = System.getProperty("java.class.path");
    StringBuffer newClassPath = new StringBuffer();
    assert classpath != null : "classpath must not be null";
    String[] paths = classpath.split(File.pathSeparator);
    for (int i = 0; i < paths.length; ++i) {
      newClassPath.append(new File(paths[i]).getAbsolutePath());
      if (i != paths.length - 1) {
        newClassPath.append(File.pathSeparator);
      }
    }

    String sdkRoot = null;

    List<String> absoluteAppServerArgs = new ArrayList<String>(appServerArgs.size());

    for (int i = 0; i < appServerArgs.size(); ++i) {
      String arg = appServerArgs.get(i);
      if (arg.startsWith("--sdk_root=")) {
        sdkRoot = new File(arg.split("=")[1]).getAbsolutePath();
        arg = "--sdk_root=" + sdkRoot;
      } else if (i == appServerArgs.size() - 1) {
        arg = new File(arg).getAbsolutePath();
      }
      absoluteAppServerArgs.add(arg);
    }

    if (sdkRoot == null) {
      sdkRoot = SdkInfo.getSdkRoot().getAbsolutePath();
    }

    String agentJar = sdkRoot + "/lib/agent/appengine-agent.jar";
    agentJar = agentJar.replace('/', File.separatorChar);
    jvmArgs.add("-javaagent:" + agentJar);

    String jdkOverridesJar = sdkRoot + "/lib/override/appengine-dev-jdk-overrides.jar";
    jdkOverridesJar = jdkOverridesJar.replace('/', File.separatorChar);
    jvmArgs.add("-Xbootclasspath/p:" + jdkOverridesJar);

    command.addAll(jvmArgs);
    command.add("-classpath");
    command.add(newClassPath.toString());
    command.add(entryClass);
    command.addAll(absoluteAppServerArgs);

    logger.fine("Executing " + command);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        if (serverProcess != null) {
          serverProcess.destroy();
        }
      }
    });

    try {
      serverProcess = builder.start();
    } catch (IOException e) {
      throw new RuntimeException("Unable to start the process", e);
    }

    new Thread(new OutputPump(serverProcess.getInputStream(),
        new PrintWriter(System.out, true))).start();
    new Thread(new OutputPump(serverProcess.getErrorStream(),
        new PrintWriter(System.err, true))).start();

    try {
      serverProcess.waitFor();
    } catch (InterruptedException e) {
    }

    serverProcess.destroy();
    serverProcess = null;
  }

  private static File newWorkingDir(String[] args) {
    if (args.length < 1) {
      DevAppServerMain.printHelp(System.out);
      System.exit(1);
    }

    File newDir = new File(args[args.length - 1]);
    DevAppServerMain.validateWarPath(newDir);
    return newDir;
  }
}
