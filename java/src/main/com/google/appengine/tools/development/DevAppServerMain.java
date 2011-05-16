// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import java.awt.Toolkit;

import com.google.appengine.tools.info.SdkInfo;
import com.google.appengine.tools.info.UpdateCheck;
import com.google.appengine.tools.util.Action;
import com.google.appengine.tools.util.Option;
import com.google.appengine.tools.util.Parser;
import com.google.appengine.tools.util.Logging;
import com.google.appengine.tools.util.Parser.ParseResult;

/**
 * The command-line entry point for DevAppServer.
 *
 */
public class DevAppServerMain {

  private static String originalTimeZone;

  private final Action ACTION = new StartAction();

  private String server = SdkInfo.getDefaultServer();

  private String address = DevAppServer.DEFAULT_HTTP_ADDRESS;
  private int port = DevAppServer.DEFAULT_HTTP_PORT;
  private boolean disableUpdateCheck;

  private final List<Option> PARSERS = Arrays.asList(
      new Option("h", "help", true) {
        @Override
        public void apply() {
          printHelp(System.err);
          System.exit(0);
        }
      },
      new Option("s", "server", false) {
        @Override
        public void apply() {
          server = getValue();
        }
      },
      new Option("a", "address", false) {
        @Override
        public void apply() {
          address = getValue();
        }
      },
      new Option("p", "port", false) {
        @Override
        public void apply() {
          port = Integer.valueOf(getValue());
        }
      },
      new Option(null, "sdk_root", false) {
        @Override
        public void apply() {
          System.setProperty("appengine.sdk.root", getValue());
        }
      },
      new Option(null, "disable_update_check", true) {
        @Override
        public void apply() {
          disableUpdateCheck = true;
        }
      }
  );

  @SuppressWarnings("unchecked")
  public static void main(String args[]) throws Exception {
    recordTimeZone();
    Logging.initializeLogging();
    if (System.getProperty("os.name").equalsIgnoreCase("Mac OS X")) {
      Toolkit.getDefaultToolkit();
    }
    new DevAppServerMain(args);
  }

  /**
   * We attempt to record user.timezone before the JVM alters its value.
   * This can happen just by asking for
   * {@link java.util.TimeZone#getDefault()}.
   *
   * We need this information later, so that we can know if the user
   * actually requested an override of the timezone. We can still be wrong
   * about this, for example, if someone directly or indirectly calls
   * {@code TimeZone.getDefault} before the main method to this class.
   * This doesn't happen in the App Engine tools themselves, but might
   * theoretically happen in some third-party tool that wraps the App Engine
   * tools. In that case, they can set {@code appengine.user.timezone}
   * to override what we're inferring for user.timezone.
   */
  private static void recordTimeZone() {
    originalTimeZone = System.getProperty("user.timezone");
  }

  public DevAppServerMain(String[] args) throws Exception {
    Parser parser = new Parser();
    ParseResult result = parser.parseArgs(ACTION, PARSERS, args);
    result.applyArgs();
  }

  public static void printHelp(PrintStream out) {
    out.println("Usage: <dev-appserver> [options] <war directory>");
    out.println("");
    out.println("Options:");
    out.println(" --help, -h                 Show this help message and exit.");
    out.println(" --server=SERVER            The server to use to determine the latest");
    out.println("  -s SERVER                   SDK version.");
    out.println(" --address=ADDRESS          The address of the interface on the local machine");
    out.println("  -a ADDRESS                  to bind to (or 0.0.0.0 for all interfaces).");
    out.println(" --port=PORT                The port number to bind to on the local machine.");
    out.println("  -p PORT");
    out.println(" --sdk_root=root            Overrides where the SDK is located.");
    out.println(" --disable_update_check     Disable the check for newer SDK versions.");
  }

  class StartAction extends Action {
    StartAction() {
      super("start");
    }

    @Override
    public void apply() {
      List<String> args = getArgs();
      if (args.size() != 1) {
        printHelp(System.err);
        System.exit(1);
      }

      try {
        File appDir = new File(args.get(0)).getCanonicalFile();
        validateWarPath(appDir);

        UpdateCheck updateCheck = new UpdateCheck(server, appDir, true);
        if (updateCheck.allowedToCheckForUpdates() && !disableUpdateCheck) {
          updateCheck.maybePrintNagScreen(System.err);
        }
        updateCheck.checkJavaVersion(System.err);

        DevAppServer server = new DevAppServerFactory().createDevAppServer(appDir, address, port);

        Map properties = System.getProperties();
        @SuppressWarnings("unchecked")
        Map<String, String> stringProperties = properties;
        setTimeZone(stringProperties);
        server.setServiceProperties(stringProperties);

        server.start();

        try {
          while (true) {
            Thread.sleep(1000 * 60 * 60);
          }
        } catch (InterruptedException e) {
        }

        System.out.println("Shutting down.");
        System.exit(0);
      } catch (Exception ex) {
        ex.printStackTrace();
        System.exit(1);
      }
    }

    private void setTimeZone(Map<String,String> serviceProperties) {
      String timeZone = serviceProperties.get("appengine.user.timezone");
      if (timeZone != null) {
        TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
      } else {
        timeZone = originalTimeZone;
      }
      serviceProperties.put("appengine.user.timezone.impl", timeZone);
    }
  }

  public static void validateWarPath(File war) {
    if (!war.exists()) {
      System.out.println("Unable to find the webapp directory " + war);
      printHelp(System.err);
      System.exit(1);
    } else if (!war.isDirectory()) {
      System.out.println("dev_appserver only accepts webapp directories, not war files.");
      printHelp(System.err);
      System.exit(1);
    }
  }
}
