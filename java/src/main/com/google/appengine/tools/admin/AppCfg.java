
// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.admin;

import com.google.appengine.tools.admin.AppAdmin.LogSeverity;
import com.google.appengine.tools.admin.AppAdminFactory.ConnectOptions;
import com.google.appengine.tools.admin.ClientLoginServerConnection.ClientAuthFailException;
import com.google.appengine.tools.admin.IndexDeleter.DeleteIndexAction;
import com.google.appengine.tools.info.SupportInfo;
import com.google.appengine.tools.info.UpdateCheck;
import com.google.appengine.tools.util.Action;
import com.google.appengine.tools.util.ClientCookieManager;
import com.google.appengine.tools.util.Logging;
import com.google.appengine.tools.util.Option;
import com.google.appengine.tools.util.Parser;
import com.google.appengine.tools.util.Parser.ParseResult;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.BackendsXml;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * The command-line SDK tool for administration of App Engine applications.
 *
 */
public class AppCfg {

  private final ConnectOptions options;
  private AppCfgAction action;
  private String applicationDirectory;
  private AppAdmin admin;
  private boolean passin;
  private boolean doJarSplitting = false;
  private Set<String> jarSplittingExcludeSuffixes = null;
  private boolean disablePrompt = false;
  private File logFile = null;
  private String compileEncoding = null;
  private final String prefsEmail;
  private LoginReader loginReader = null;

  public static void main(String[] args) {
    Logging.initializeLogging();
    new AppCfg(args);
  }

  protected AppCfg(String[] cmdLineArgs) {
    this(new AppAdminFactory(), cmdLineArgs);
  }

  public AppCfg(AppAdminFactory factory, String[] cmdLineArgs) {
    options = new ConnectOptions();
    Parser parser = new Parser();

    PrintWriter logWriter;

    try {
      logFile = File.createTempFile("appcfg", ".log");
      logWriter = new PrintWriter(new FileWriter(logFile), true);
    } catch (IOException e) {
      throw new RuntimeException("Unable to enable logging.", e);
    }

    String prefsEmail = null;

    try {
      ParseResult result = parser.parseArgs(actions, optionParsers, cmdLineArgs);
      action = (AppCfgAction) result.getAction();
      try {
        result.applyArgs();
      } catch (IllegalArgumentException e) {
        e.printStackTrace(logWriter);
        System.out.println("Bad argument: " + e.getMessage());
        System.out.println(action.getHelpString());
        System.exit(1);
      }
      if (System.getProperty("http.proxyHost") != null &&
          System.getProperty("https.proxyHost") == null) {
        System.setProperty("https.proxyHost",
            System.getProperty("http.proxyHost"));
        if (System.getProperty("http.proxyPort") != null &&
            System.getProperty("https.proxyPort") == null) {
          System.setProperty("https.proxyPort",
              System.getProperty("http.proxyPort"));
        }
      }
      File appDirectoryFile = new File(applicationDirectory);
      validateWarPath(appDirectoryFile);

      UpdateCheck updateCheck = new UpdateCheck(options.getServer(), appDirectoryFile,
          options.getSecure());
      updateCheck.maybePrintNagScreen(System.out);
      updateCheck.checkJavaVersion(System.out);

      prefsEmail = loadCookies(options);

      factory.setJarSplittingEnabled(doJarSplitting);
      if (jarSplittingExcludeSuffixes != null) {
        factory.setJarSplittingExcludes(jarSplittingExcludeSuffixes);
      }
      if (compileEncoding != null) {
        factory.setCompileEncoding(compileEncoding);
      }
      System.out.println("Reading application configuration data...");
      Application app = Application.readApplication(applicationDirectory);

      app.setListener(new UpdateListener() {
          public void onProgress(UpdateProgressEvent event) {
            System.out.println(event.getPercentageComplete() + "% " + event.getMessage());
          }

          public void onSuccess(UpdateSuccessEvent event) {
            System.out.println("Operation complete.");
          }

          public void onFailure(UpdateFailureEvent event) {
            System.out.println(event.getFailureMessage());
          }
        });

      admin = factory.createAppAdmin(options, app, logWriter);

      System.out.println("Beginning server interaction for " + app.getAppId() + "...");

      try {
        action.execute();
      } catch (AdminException ex) {
        System.out.println(ex.getMessage());
        ex.printStackTrace(logWriter);
        printLogLocation();
        System.exit(1);
      }
      System.out.println("Success.");

      if (!options.getRetainUploadDir()) {
        System.out.println("Cleaning up temporary files...");
        app.cleanStagingDirectory();
      } else {
        File stage = app.getStagingDir();
        if (stage == null) {
          System.out.println("Temporary staging directory was not needed, and not created");
        } else {
          System.out.println("Temporary staging directory left in " + stage.getCanonicalPath());
        }
      }

    } catch (IllegalArgumentException e) {
      e.printStackTrace(logWriter);
      System.out.println("Bad argument: " + e.getMessage());
      printHelp();
      System.exit(1);
    } catch (AppEngineConfigException e) {
      e.printStackTrace(logWriter);
      System.out.println("Bad configuration: " + e.getMessage());
      if (e.getCause() != null) {
        System.out.println("  Caused by: " + e.getCause().getMessage());
      }
      printLogLocation();
      System.exit(1);
    } catch (Exception e) {
      System.out.println("Encountered a problem: " + e.getMessage());
      e.printStackTrace(logWriter);
      printLogLocation();
      System.exit(1);
    }
    this.prefsEmail = prefsEmail;
  }

  /**
   * Prints a uniform message to direct the user to the given logfile for
   * more information.
   */
  private void printLogLocation() {
    if (logFile != null) {
      System.out.println("Please see the logs [" + logFile.getAbsolutePath() +
          "] for further information.");
    }
  }

  private String loadCookies(final ConnectOptions options) {
    Preferences prefs = Preferences.userNodeForPackage(ServerConnection.class);
    String prefsEmail = prefs.get("email", null);

    if (prefsEmail != null) {
      ClientCookieManager cookies = null;
      byte[] serializedCookies = prefs.getByteArray("cookies", null);
      if (serializedCookies != null) {
        try {
          cookies = (ClientCookieManager)
              new ObjectInputStream(
                  new ByteArrayInputStream(serializedCookies)).readObject();
        } catch (ClassNotFoundException ex) {
        } catch (IOException ex) {
        }
      }

      if (options.getUserId() == null ||
          prefsEmail.equals(options.getUserId())) {
        options.setCookies(cookies);
      }
    }

    options.setPasswordPrompt(new AppAdminFactory.PasswordPrompt() {
        public String getPassword() {
          doPrompt();
          options.setUserId(loginReader.getUsername());
          return loginReader.getPassword();
        }
      });
    return prefsEmail;
  }

  private void doPrompt() {

    if (disablePrompt) {
      System.out.println("Your authentication credentials can't be found and may have expired.\n" +
        "Please run appcfg directly from the command line to re-establish your credentials.");
      System.exit(1);
    }

    getLoginReader().doPrompt();

  }

  private LoginReader getLoginReader() {
    if (loginReader == null) {
      loginReader = LoginReaderFactory.createLoginReader(options, passin, prefsEmail);
    }
    return loginReader;
  }

  private static final String GENERAL_OPTION_HELP =
      "  -s SERVER, --server=SERVER\n"
    + "                        The server to connect to.\n"
    + "  -e EMAIL, --email=EMAIL\n"
    + "                        The username to use. Will prompt if omitted.\n"
    + "  -H HOST, --host=HOST  Overrides the Host header sent with all RPCs.\n"
    + "  -p PROXYHOST[:PORT], --proxy=PROXYHOST[:PORT]\n"
    + "                        Proxies requests through the given proxy server.\n"
    + "                        If --proxy_https is also set, only HTTP will be\n"
    + "                        proxied here, otherwise both HTTP and HTTPS will.\n"
    + "  --proxy_https=PROXYHOST[:PORT]\n"
    + "                        Proxies HTTPS requests through the given proxy server.\n"
    + "  --sdk_root=root       Overrides where the SDK is located.\n"
    + "  --passin              Always read the login password from stdin.\n"
    + "  --insecure            Do not use HTTPS to communicate with the Admin Console.\n";

  private static final String UPDATE_OPTION_HELP =
      "  --enable_jar_splitting\n"
    + "                        Split large jar files (> 10M) into smaller fragments.\n"
    + "  --jar_splitting_excludes=SUFFIXES\n"
    + "                        When --enable-jar-splitting is set, files that match\n"
    + "                        the list of comma separated SUFFIXES will be excluded\n"
    + "                        from all jars.\n"
    + "  --retain_upload_dir\n"
    + "                        Do not delete temporary (staging) directory used in\n"
    + "                        uploading.\n"
    + "  --compile_encoding\n"
    + "                        The character encoding to use when compiling JSPs.\n";

  private static final String LOG_OPTION_HELP =
      "  -n NUM_DAYS, --num_days=NUM_DAYS\n"
    + "                        Number of days worth of log data to get. The cut-off\n"
    + "                        point is midnight UTC. Use 0 to get all available\n"
    + "                        logs. Default is 1.\n"
    + "  --severity=SEVERITY   Severity of app-level log messages to get. The range\n"
    + "                        is 0 (DEBUG) through 4 (CRITICAL). If omitted, only\n"
    + "                        request logs are returned.\n"
    + "  -a, --append          Append to existing file.\n";

  private static final String CRON_OPTION_HELP =
      "  -n NUM_RUNS, --num_runs=NUM_RUNS\n"
    + "                        Number of scheduled execution times to compute\n";

  private static final String VACUUM_OPTIONS_HELP =
      "  -f, --force           Force deletion of indexes without being prompted.\n";

  private static void printHelp() {
    String help = "usage: AppCfg [options] <action> <app-dir> [<argument>]\n"
        + "\n" + "Action must be one of:\n"
        + "  help: Print help for a specific action.\n"
        + "  request_logs: Write request logs in Apache common log format.\n"
        + "  rollback: Rollback an in-progress update.\n"
        + "  update: Create or update an app version.\n"
        + "  update_indexes: Update application indexes.\n"
        + "  update_cron: Update application cron jobs.\n"
        + "  update_queues: Update application task queue definitions.\n"
        + "  update_dos: Update application DoS protection configuration.\n"
        + "  version: Prints version information.\n"
        + "  cron_info: Displays times for the next several runs of each cron job.\n"
        + "  vacuum_indexes: Delete unused indexes from application.\n"
        + "  backends list: List the currently configured backends.\n"
        + "  backends update: Update the specified backend or all backends.\n"
        + "  backends rollback: Roll back a previously in-progress update.\n"
        + "  backends start: Start the specified backend.\n"
        + "  backends stop: Stop the specified backend.\n"
        + "  backends delete: Delete the specified backend.\n"
        + "  backends configure: Configure the specified backend.\n"
        + "Use 'help <action>' for a detailed description.\n" + "\n" + "options:\n"
        + "  -h, --help            Show the help message and exit.\n"
        + GENERAL_OPTION_HELP
        + UPDATE_OPTION_HELP
        + LOG_OPTION_HELP
        + CRON_OPTION_HELP;

    System.out.println(help);
  }

  private final List<Option> optionParsers = Arrays.asList(

      new Option("h", "help", true) {
        @Override
        public void apply() {
          printHelp();
          System.exit(1);
        }
      },

      new Option("s", "server", false) {
        @Override
        public void apply() {
          options.setServer(getValue());
        }
      },

      new Option("e", "email", false) {
        @Override
        public void apply() {
          options.setUserId(getValue());
        }
      },

      new Option("H", "host", false) {
        @Override
        public void apply() {
          options.setHost(getValue());
        }
      },

      new Option("p", "proxy", false) {
        @Override
        public void apply() {
          HostPort hostport = new HostPort(getValue());

          System.setProperty("http.proxyHost", hostport.getHost());
          if (hostport.hasPort()) {
            System.setProperty("http.proxyPort", hostport.getPort());
          }
        }
      },

      new Option(null, "proxy_https", false) {
        @Override
        public void apply() {
          HostPort hostport = new HostPort(getValue());

          System.setProperty("https.proxyHost", hostport.getHost());
          if (hostport.hasPort()) {
            System.setProperty("https.proxyPort", hostport.getPort());
          }
        }
      },

      new Option(null, "insecure", true) {
        @Override
        public void apply() {
          options.setSecure(false);
        }
      },

      new Option("f", "force", true) {
        @Override
        public void apply(){
          if (action instanceof VacuumIndexesAction){
            VacuumIndexesAction viAction = (VacuumIndexesAction) action;
            viAction.promptUserForEachDelete = false;
          }
        }
      },

      new Option("a", "append", true) {
        @Override
        public void apply() {
          RequestLogsAction logsAction = (RequestLogsAction) action;
          logsAction.append = true;
        }
      },

      new Option("n", "num_days", false) {
        @Override
        public void apply() {
          if (action instanceof RequestLogsAction) {
            RequestLogsAction logsAction = (RequestLogsAction) action;
            try {
              logsAction.numDays = Integer.parseInt(getValue());
            } catch (NumberFormatException e) {
              throw new IllegalArgumentException("num_days must be an integral number.");
            }
          } else {
            CronInfoAction croninfoAction = (CronInfoAction) action;
            croninfoAction.setNumRuns(getValue());
          }
        }
      },

      new Option(null, "num_runs", false) {
        @Override
        public void apply() {
          CronInfoAction croninfoAction = (CronInfoAction) action;
          croninfoAction.setNumRuns(getValue());
        }
      },

      new Option(null, "severity", false) {
        @Override
        public void apply() {
          RequestLogsAction logsAction = (RequestLogsAction) action;
          try {
            int severity = Integer.parseInt(getValue());
            int maxSeverity = LogSeverity.values().length;
            if (severity < 0 || severity > maxSeverity) {
              throw new IllegalArgumentException("severity must be between 0 and " + maxSeverity);
            }
            logsAction.severity = severity;
          } catch (NumberFormatException e) {
            for (Enum severity : LogSeverity.values()) {
              if (getValue().equalsIgnoreCase(severity.toString())) {
                logsAction.severity = severity.ordinal();
                return;
              }
            }
            throw new IllegalArgumentException("severity must be an integral "
                + "number 0-4, or one of DEBUG, INFO, WARN, ERROR, CRITICAL");
          }
        }
      },

      new Option(null, "sdk_root", false) {
        @Override
        public void apply() {
          options.setSdkRoot(getValue());
        }
      },

      new Option(null, "enable_jar_splitting", true) {
        @Override
        public void apply() {
          doJarSplitting = true;
        }
      },

      new Option(null, "jar_splitting_excludes", false) {
        @Override
        public void apply() {
          jarSplittingExcludeSuffixes = new HashSet<String>(Arrays.asList(getValue().split(",")));
        }
      },

      new Option(null, "retain_upload_dir", true) {
        @Override
        public void apply() {
          options.setRetainUploadDir(true);
        }
      },

      new Option(null, "passin", true) {
        @Override
        public void apply() {
          passin = true;
        }
      },

      new Option(null, "compile_encoding", false) {
        @Override
        public void apply() {
          compileEncoding = getValue();
        }
      },

      new Option(null, "disable_prompt", true) {
        @Override
        public void apply() {
          disablePrompt = true;
        }
      });

  private final List<Action> actions = Arrays.<Action>asList(
      new UpdateAction(),
      new RequestLogsAction(),
      new RollbackAction(),
      new UpdateIndexesAction(),
      new UpdateCronAction(),
      new UpdateDosAction(),
      new UpdateQueueAction(),
      new CronInfoAction(),
      new VacuumIndexesAction(),
      new HelpAction(),
      new VersionAction(),
      new BackendsListAction(),
      new BackendsRollbackAction(),
      new BackendsUpdateAction(),
      new BackendsStartAction(),
      new BackendsStopAction(),
      new BackendsDeleteAction(),
      new BackendsConfigureAction(),
      new BackendsAction()
  );

  abstract class AppCfgAction extends Action {

    AppCfgAction(String... names) {
      super(names);
    }

    @Override
    protected void setArgs(List<String> args) {
      super.setArgs(args);
    }

    @Override
    public void apply() {
      if (getArgs().size() < 1) {
        throw new IllegalArgumentException("Expected the application directory"
            + " as an argument after the action name.");
      }
      applicationDirectory = getArgs().get(0);
    }
    public abstract void execute();

    /**
     * Supplies a help message suitable for the user.
     */
    public abstract String getHelpString();
  }

  class UpdateAction extends AppCfgAction {
    UpdateAction() {
      super("update");
    }
    @Override
    public void execute() {
      admin.update(new AppCfgUpdateListener());
    }

    @Override
    public String getHelpString() {
      return "AppCfg [options] update <app-dir>\n\n" +
        "Installs a new version of the application onto the server, as the\n" +
        "default version for end users.\n\n" +
        "Options:\n" + GENERAL_OPTION_HELP + UPDATE_OPTION_HELP;
    }
  }

  class RequestLogsAction extends AppCfgAction {
    String outputFile;
    int numDays = 1;
    int severity = -1;
    boolean append = false;

    RequestLogsAction() {
      super("request_logs");
    }
    @Override
    public void apply() {
      super.apply();
      if (getArgs().size() != 2) {
        throw new IllegalArgumentException("Expected the application directory"
            + " and log file as arguments after the request_logs action name.");
      }
      outputFile = getArgs().get(1);
    }
    @Override
    public void execute() {
      Reader reader = admin.requestLogs(numDays,
         severity >= 0 ? LogSeverity.values()[severity] : null);
      if (reader == null) {
        return;
      }

      BufferedReader r = new BufferedReader(reader);
      PrintWriter writer = null;
      try {
        writer = new PrintWriter(new FileWriter(outputFile, append));
        String line = null;
        while ((line = r.readLine()) != null) {
          writer.println(line);
        }
      } catch (IOException e) {
        throw new RuntimeException("Failed to read logs: " + e);
      } finally {
        if (writer != null) {
          writer.close();
        }
        try {
          r.close();
        } catch (IOException e) {
        }
      }
    }

    @Override
    public String getHelpString() {
      return "AppCfg [options] request_logs <app-dir> <output-file>\n\n" +
        "Populates the output-file with recent logs from the application.\n\n" +
        "Options:\n" + GENERAL_OPTION_HELP + LOG_OPTION_HELP;
    }
  }

  class RollbackAction extends AppCfgAction {
    RollbackAction() {
      super("rollback");
    }
    @Override
    public void execute() {
      admin.rollback();
    }
    @Override
    public String getHelpString() {
      return "AppCfg [options] rollback <app-dir>\n\n"
          + "The 'update' command requires a server-side transaction. "
          + "Use 'rollback' if you experience an error during 'update' "
          + "and want to begin a new update transaction." + "Options:\n" + GENERAL_OPTION_HELP;
    }
  }

  class UpdateIndexesAction extends AppCfgAction {
    UpdateIndexesAction() {
      super("update_indexes");
    }
    @Override
    public void execute() {
      admin.updateIndexes();
    }
    @Override
    public String getHelpString() {
      return "AppCfg [options] update_indexes <app-dir>\n\n" +
        "Updates the datastore indexes for the server to add any in the current\n" +
        "application directory.  Does not alter the running application version, nor\n" +
        "remove any existing indexes.\n\n" +
        "Options:\n" + GENERAL_OPTION_HELP;
    }
  }

  class UpdateCronAction extends AppCfgAction {
    UpdateCronAction() {
      super("update_cron");
    }
    @Override
    public void execute() {
      admin.updateCron();
    }
    @Override
    public String getHelpString() {
      return "AppCfg [options] update_cron <app-dir>\n\n" +
        "Updates the cron jobs for the server. Updates any new, removed or changed.\n" +
        "cron jobs. Does not otherwise alter the running application version.\n\n" +
        "Options:\n" + GENERAL_OPTION_HELP;
    }
  }

  class UpdateDosAction extends AppCfgAction {
    UpdateDosAction() {
      super("update_dos");
    }
    @Override
    public void execute() {
      admin.updateDos();
    }
    @Override
    public String getHelpString() {
      return "AppCfg [options] update_dos <app-dir>\n\n" +
        "Updates the DoS protection configuration for the server.\n" +
        "Does not otherwise alter the running application version.\n\n" +
        "Options:\n" + GENERAL_OPTION_HELP;
    }
  }

  class UpdateQueueAction extends AppCfgAction {
    UpdateQueueAction() {
      super("update_queues");
    }
    @Override
    public void execute() {
      admin.updateQueues();
    }
    @Override
    public String getHelpString() {
      return "AppCfg [options] " + getNameString() + " <app-dir>\n\n" +
        "Updates any new, removed or changed task queue definitions.\n" +
        "Does not otherwise alter the running application version.\n\n" +
        "Options:\n" + GENERAL_OPTION_HELP;
    }
  }

  class CronInfoAction extends AppCfgAction {
    int numRuns = 5;

    CronInfoAction() {
      super("cron_info");
    }
    @Override
    public void execute() {
      List<CronEntry> entries = admin.cronInfo();
      if (entries.size() == 0) {
        System.out.println("No cron jobs defined.");
      } else {
        System.out.println(entries.size() + " cron entries defined.\n");
        for (CronEntry entry : entries) {
          System.out.println(entry.toXml());
          System.out.println("Next " + numRuns + " execution times:");
          Iterator<String> iter = entry.getNextTimesIterator();
          for (int i = 0; i < numRuns; i++) {
            System.out.println("  " + iter.next());
          }
          System.out.println("");
        }
      }
    }
    @Override
    public String getHelpString() {
      return "AppCfg [options] cron_info <app-dir>\n\n" +
        "Displays times for the next several runs of each cron job.\n\n" +
        "Options:\n" + GENERAL_OPTION_HELP + CRON_OPTION_HELP;
    }
    public void setNumRuns(String numberString) {
      try {
        numRuns = Integer.parseInt(numberString);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("num_runs must be an integral number.");
      }
      if (numRuns < 0) {
        throw new IllegalArgumentException("num_runs must be positive.");
      }
    }
  }

  class VacuumIndexesAction extends AppCfgAction {
    public boolean promptUserForEachDelete = true;

    VacuumIndexesAction() {
      super("vacuum_indexes");
    }

    @Override
    public void execute() {
      ConfirmationCallback<IndexDeleter.DeleteIndexAction> callback = null;
      if (promptUserForEachDelete) {
        callback = new ConfirmationCallback<IndexDeleter.DeleteIndexAction>() {
          @Override
          public Response confirmAction(DeleteIndexAction action) {
            while (true) {
              String prompt = "\n" + action.getPrompt() + " (N/y/a): ";
              System.out.print(prompt);
              System.out.flush();
              BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
              String response;
              try {
                response = in.readLine();
              } catch (IOException ioe) {
                response = null;
              }
              response = (null == response ? "" : response.trim().toLowerCase());
              if ("y".equals(response)) {
                return Response.YES;
              }
              if ("n".equals(response) || response.isEmpty()) {
                return Response.NO;
              }
              if ("a".equals(response)) {
                return Response.YES_ALL;
              }
            }
          }
        };
      }
      admin.vacuumIndexes(callback, new AppCfgVacuumIndexesListener());
    }

    @Override
    public String getHelpString() {
      return "AppCfg [options] vacuum_indexes <app-dir>\n\n"
          + "Deletes indexes on the server that are not present in the local\n"
          + "index configuration file.  The user is prompted before each delete."
          + "\n\nOptions:\n" + GENERAL_OPTION_HELP + VACUUM_OPTIONS_HELP;
    }
  }

  class HelpAction extends AppCfgAction {
    HelpAction() {
      super("help");
    }
    @Override
    public void apply() {
      if (getArgs().isEmpty()) {
        printHelp();
      } else {
        Action foundAction = Parser.lookupAction(actions, getArgs().toArray(new String[0]), 0);
        if (foundAction == null) {
          System.out.println("No such command \"" + getArgs().get(0) + "\"\n\n");
          printHelp();
        } else {
          System.out.println(((AppCfgAction) foundAction).getHelpString());
        }
      }
      System.exit(1);
    }
    @Override
    public void execute() {
    }
    @Override
    public String getHelpString() {
      return "AppCfg help <command>\n\n" +
          "Prints help about a specific command.\n";
    }
  }

  class VersionAction extends AppCfgAction {
    VersionAction() {
      super("version");
    }
    @Override
    public void apply() {
      System.out.println(SupportInfo.getVersionString());
      System.exit(0);
    }
    @Override
    public void execute() {
    }
    @Override
    public String getHelpString() {
      return "AppCfg version\n\n" +
          "Prints version information.\n";
    }
  }

  class BackendsListAction extends AppCfgAction {
    BackendsListAction() {
      super("backends", "list");
    }

    @Override
    public void execute() {
      for (BackendsXml.Entry backend : admin.listBackends()) {
        System.out.println(backend.toString());
      }
    }

    @Override
    public String getHelpString() {
      return "AppCfg [options] backends list <app-dir>\n\n" +
        "List the currently configured backends.\n\n" +
        "Options:\n" + GENERAL_OPTION_HELP;
    }
  }

  class BackendsRollbackAction extends AppCfgAction {
    private String backendName;

    BackendsRollbackAction() {
      super("backends", "rollback");
    }

    @Override
    public void apply() {
      super.apply();
      if (getArgs().size() < 1 || getArgs().size() > 2) {
        throw new IllegalArgumentException("Expected <app-dir> [<backend-name>]");
      } else if (getArgs().size() == 2) {
        backendName = getArgs().get(1);
      }
    }

    @Override
    public void execute() {
      List<String> backends;
      if (backendName != null) {
        admin.rollbackBackend(backendName);
      } else {
        admin.rollbackAllBackends();
      }
    }

    @Override
    public String getHelpString() {
      return "AppCfg [options] backends rollback <app-dir> [<backend-name>]\n\n"
          + "The 'backends update' command requires a server-side transaction. "
          + "Use 'backends rollback' if you experience an error during 'backends update'"
          + " and want to begin a new update transaction." + "Options:\n" + GENERAL_OPTION_HELP;
    }
  }

  class BackendsUpdateAction extends AppCfgAction {
    private String backendName;

    BackendsUpdateAction() {
      super("backends", "update");
    }

    @Override
    public void apply() {
      super.apply();
      if (getArgs().size() < 1 || getArgs().size() > 2) {
        throw new IllegalArgumentException("Expected <app-dir> [<backend-name>]");
      } else if (getArgs().size() == 2) {
        backendName = getArgs().get(1);
      }
    }

    @Override
    public void execute() {
      List<String> backends;
      if (backendName != null) {
        admin.updateBackend(backendName, new AppCfgUpdateListener());
      } else {
        admin.updateAllBackends(new AppCfgUpdateListener());
      }
    }

    @Override
    public String getHelpString() {
      return "AppCfg [options] backends update <app-dir> [<backend-name>]\n\n" +
        "Update the specified backend or all backends.\n\n" +
        "Options:\n" + GENERAL_OPTION_HELP;
    }
  }

  class BackendsStartAction extends AppCfgAction {
    private String backendName;

    BackendsStartAction() {
      super("backends", "start");
    }

    @Override
    public void apply() {
      super.apply();
      if (getArgs().size() != 2) {
        throw new IllegalArgumentException("Expected the backend name");
      }
      backendName = getArgs().get(1);
    }

    @Override
    public void execute() {
      admin.setBackendState(backendName, BackendsXml.State.START);
    }

    @Override
    public String getHelpString() {
      return "AppCfg [options] backends start <app-dir> <backend>\n\n" +
        "Starts the backend with the specified name.\n\n" +
        "Options:\n" + GENERAL_OPTION_HELP;
    }
  }

  class BackendsStopAction extends AppCfgAction {
    private String backendName;

    BackendsStopAction() {
      super("backends", "stop");
    }

    @Override
    public void apply() {
      super.apply();
      if (getArgs().size() != 2) {
        throw new IllegalArgumentException("Expected the backend name");
      }
      backendName = getArgs().get(1);
    }
    @Override
    public void execute() {
      admin.setBackendState(backendName, BackendsXml.State.STOP);
    }

    @Override
    public String getHelpString() {
      return "AppCfg [options] backends stop <app-dir> <backend>\n\n" +
        "Stops the backend with the specified name.\n\n" +
        "Options:\n" + GENERAL_OPTION_HELP;
    }
  }

  class BackendsDeleteAction extends AppCfgAction {
    private String backendName;

    BackendsDeleteAction() {
      super("backends", "delete");
    }

    @Override
    public void apply() {
      super.apply();
      if (getArgs().size() != 2) {
        throw new IllegalArgumentException("Expected the backend name");
      }
      backendName = getArgs().get(1);
    }
    @Override
    public void execute() {
      admin.deleteBackend(backendName);
    }

    @Override
    public String getHelpString() {
      return "AppCfg [options] backends delete\n\n" +
        "Deletes the specified backend.\n\n" +
        "Options:\n" + GENERAL_OPTION_HELP;
    }
  }

  class BackendsConfigureAction extends AppCfgAction {
    private String backendName;

    BackendsConfigureAction() {
      super("backends", "configure");
    }

    @Override
    public void apply() {
      super.apply();
      if (getArgs().size() != 2) {
        throw new IllegalArgumentException("Expected the backend name");
      }
      backendName = getArgs().get(1);
    }
    @Override
    public void execute() {
      admin.configureBackend(backendName);
    }

    @Override
    public String getHelpString() {
      return "AppCfg [options] backends configure <app-dir> <backend>\n\n" +
          "Updates the configuration of the backend with the specified name, without " +
          "stopping instances that are currently running.  Only valid for certain " +
          "settings (instances, options: failfast, options: public).\n\n" +
          "Options:\n" + GENERAL_OPTION_HELP;
    }
  }

  /**
   * This is a catchall for the case where the user enters "appcfg.sh
   * backends app-dir sub-command" rather than "appcfg.sh backends
   * sub-command app-dir".  It was added to maintain compatibility
   * with Python.  It simply remaps the arguments and dispatches the
   * appropriate action.
   */
  class BackendsAction extends AppCfgAction {
    private AppCfgAction subAction;

    BackendsAction() {
      super("backends");
    }

    @Override
    public void apply() {
      super.apply();
      if (getArgs().size() < 2) {
        throw new IllegalArgumentException("Expected backends <app-dir> <sub-command> [...]");
      }
      String dir = getArgs().get(0);
      String subCommand = getArgs().get(1);
      subAction = (AppCfgAction) Parser.lookupAction(actions,
                                                     new String[] { "backends", subCommand },
                                                     0);
      if (subAction instanceof BackendsAction) {
        throw new IllegalArgumentException("Unknown backends subcommand.");
      }
      List<String> newArgs = new ArrayList<String>();
      newArgs.add(dir);
      newArgs.addAll(getArgs().subList(2, getArgs().size()));
      subAction.setArgs(newArgs);
      subAction.apply();
    }

    @Override
    public void execute() {
      subAction.execute();
    }

    @Override
    public String getHelpString() {
      return "AppCfg [options] backends list: List the currently configured backends.\n"
          + "AppCfg [options] backends update: Update the specified backend or all backends.\n"
          + "AppCfg [options] backends rollback: Roll back a previously in-progress update.\n"
          + "AppCfg [options] backends start: Start the specified backend.\n"
          + "AppCfg [options] backends stop: Stop the specified backend.\n"
          + "AppCfg [options] backends delete: Delete the specified backend.\n"
          + "AppCfg [options] backends configure: Configure the specified backend.\n";
    }
  }

  private static class AppCfgListener implements UpdateListener {
    private String operationName;

    AppCfgListener(String opName){
      operationName = opName;
    }
    public void onProgress(UpdateProgressEvent event) {
      System.out.println(event.getPercentageComplete() + "% " + event.getMessage());
    }

    public void onSuccess(UpdateSuccessEvent event) {
      String details = event.getDetails();
      if (details.length() > 0) {
        System.out.println();
        System.out.println("Details:");
        System.out.println(details);
      }

      System.out.println();
      System.out.println(operationName + " completed successfully.");
    }

    public void onFailure(UpdateFailureEvent event) {
      String details = event.getDetails();
      if (details.length() > 0) {
        System.out.println();
        System.out.println("Error Details:");
        System.out.println(details);
      }

      System.out.println();
      String failMsg = event.getFailureMessage();
      System.out.println(failMsg);
      if (event.getCause() instanceof ClientAuthFailException) {
        System.out.println("Consider using the -e EMAIL option if that"
                           + " email address is incorrect.");
      }
    }
  }

  private static class AppCfgUpdateListener extends AppCfgListener {
    AppCfgUpdateListener(){
      super("Update");
    }
  }

  private static class AppCfgVacuumIndexesListener extends AppCfgListener {
    AppCfgVacuumIndexesListener(){
      super("vacuum_indexes");
    }
  }

  private static class HostPort {
    private String host;
    private String port;

    public HostPort(String hostport) {
      int colon = hostport.indexOf(':');
      host = colon < 0 ? hostport : hostport.substring(0, colon);
      port = colon < 0 ? "" : hostport.substring(colon + 1);
    }

    public String getHost() {
      return host;
    }

    public String getPort() {
      return port;
    }

    public boolean hasPort() {
      return port.length() > 0;
    }
  }

  private static void validateWarPath(File war) {
    if (!war.exists()) {
      System.out.println("Unable to find the webapp directory " + war);
      printHelp();
      System.exit(1);
    } else if (!war.isDirectory()) {
      System.out.println("appcfg only accepts webapp directories, not war files.");
      printHelp();
      System.exit(1);
    }
  }
}
