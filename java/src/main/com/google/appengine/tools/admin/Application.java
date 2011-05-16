// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.admin;

import com.google.appengine.tools.admin.AppAdminFactory.ApplicationProcessingOptions;
import com.google.appengine.tools.info.SdkImplInfo;
import com.google.appengine.tools.info.SdkInfo;
import com.google.appengine.tools.util.ApiVersionFinder;
import com.google.appengine.tools.util.FileIterator;
import com.google.appengine.tools.util.JarSplitter;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;
import com.google.apphosting.utils.config.AppYaml;
import com.google.apphosting.utils.config.BackendsXml;
import com.google.apphosting.utils.config.BackendsXmlReader;
import com.google.apphosting.utils.config.BackendsYamlReader;
import com.google.apphosting.utils.config.CronXml;
import com.google.apphosting.utils.config.CronXmlReader;
import com.google.apphosting.utils.config.CronYamlReader;
import com.google.apphosting.utils.config.DosXml;
import com.google.apphosting.utils.config.DosXmlReader;
import com.google.apphosting.utils.config.DosYamlReader;
import com.google.apphosting.utils.config.GenerationDirectory;
import com.google.apphosting.utils.config.IndexesXml;
import com.google.apphosting.utils.config.IndexesXmlReader;
import com.google.apphosting.utils.config.QueueXml;
import com.google.apphosting.utils.config.QueueXmlReader;
import com.google.apphosting.utils.config.QueueYamlReader;
import com.google.apphosting.utils.config.WebXml;
import com.google.apphosting.utils.config.WebXmlReader;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

/**
 * An App Engine application. You can {@link #readApplication read} an
 * {@code Application} from a path, and
 * {@link com.google.appengine.tools.admin.AppAdminFactory#createAppAdmin create}
 * an {@link com.google.appengine.tools.admin.AppAdmin} to upload, create
 * indexes, or otherwise manage it.
 *
 */
public class Application {

  private static Pattern JSP_REGEX = Pattern.compile(".*\\.jspx?");

  /** If available, this is set to a program to make symlinks, e.g. /bin/ln */
  private static File ln = Utility.findLink();
  private static File sdkDocsDir;
  private static synchronized File getSdkDocsDir(){
    if (null == sdkDocsDir){
      sdkDocsDir = new File(SdkInfo.getSdkRoot(), "docs");
    }
    return sdkDocsDir;
  }

  private static final String STAGEDIR_PREFIX = "appcfg";

  private static final Logger logger = Logger.getLogger(Application.class.getName());

  private AppEngineWebXml appEngineWebXml;
  private WebXml webXml;
  private CronXml cronXml;
  private DosXml dosXml;
  private QueueXml queueXml;
  private IndexesXml indexesXml;
  private BackendsXml backendsXml;
  private File baseDir;
  private File stageDir;
  private String apiVersion;

  private UpdateListener listener;
  private PrintWriter detailsWriter;
  private int updateProgress = 0;
  private int progressAmount = 0;

  protected Application(){
  }

  private Application(String explodedPath) {
    this.baseDir = new File(explodedPath);
    explodedPath = baseDir.getPath();
    if (File.separatorChar == '\\') {
      explodedPath = explodedPath.replace('\\', '/');
    }
    File webinf = new File(baseDir, "WEB-INF");
    if (!webinf.getName().equals("WEB-INF")) {
      throw new AppEngineConfigException("WEB-INF directory must be capitalized.");
    }

    String webinfPath = webinf.getPath();
    AppEngineWebXmlReader aewebReader = new AppEngineWebXmlReader(explodedPath);
    WebXmlReader webXmlReader = new WebXmlReader(explodedPath);
    AppYaml.convert(webinf, aewebReader.getFilename(), webXmlReader.getFilename());

    validateXml(aewebReader.getFilename(), new File(getSdkDocsDir(), "appengine-web.xsd"));
    appEngineWebXml = aewebReader.readAppEngineWebXml();
    appEngineWebXml.setSourcePrefix(explodedPath);

    webXml = webXmlReader.readWebXml();
    webXml.validate();

    CronXmlReader cronReader = new CronXmlReader(explodedPath);
    validateXml(cronReader.getFilename(), new File(getSdkDocsDir(), "cron.xsd"));
    cronXml = cronReader.readCronXml();
    if (cronXml == null) {
      CronYamlReader cronYaml = new CronYamlReader(webinfPath);
      cronXml = cronYaml.parse();
    }

    QueueXmlReader queueReader = new QueueXmlReader(explodedPath);
    validateXml(queueReader.getFilename(), new File(getSdkDocsDir(), "queue.xsd"));
    queueXml = queueReader.readQueueXml();
    if (queueXml == null) {
      QueueYamlReader queueYaml = new QueueYamlReader(webinfPath);
      queueXml = queueYaml.parse();
    }

    DosXmlReader dosReader = new DosXmlReader(explodedPath);
    validateXml(dosReader.getFilename(), new File(getSdkDocsDir(), "dos.xsd"));
    dosXml = dosReader.readDosXml();
    if (dosXml == null) {
      DosYamlReader dosYaml = new DosYamlReader(webinfPath);
      dosXml = dosYaml.parse();
    }

    IndexesXmlReader indexReader = new IndexesXmlReader(explodedPath);
    validateXml(indexReader.getFilename(), new File(getSdkDocsDir(), "datastore-indexes.xsd"));
    indexesXml = indexReader.readIndexesXml();

    BackendsXmlReader backendsReader = new BackendsXmlReader(explodedPath);
    validateXml(backendsReader.getFilename(), new File(getSdkDocsDir(), "backends.xsd"));
    backendsXml = backendsReader.readBackendsXml();
    if (backendsXml == null) {
      BackendsYamlReader backendsYaml = new BackendsYamlReader(webinfPath);
      backendsXml = backendsYaml.parse();
    }
  }

  /**
   * Reads the App Engine application from {@code path}. The path may either
   * be a WAR file or the root of an exploded WAR directory.
   *
   * @param path a not {@code null} path.
   *
   * @throws IOException if an error occurs while trying to read the {@code Application}
   * @throws com.google.apphosting.utils.config.AppEngineConfigException if the
   * {@code Application's} appengine-web.xml file is malformed.
   */
  public static Application readApplication(String path)
      throws IOException {
    return new Application(path);
  }

  /**
   * Returns the application identifier, from the AppEngineWebXml config
   * @return application identifier
   */
  public String getAppId() {
    return appEngineWebXml.getAppId();
  }

  /**
   * Returns the application version, from the AppEngineWebXml config
   * @return application version
   */
  public String getVersion() {
    return appEngineWebXml.getMajorVersionId();
  }

  /**
   * Returns the AppEngineWebXml describing the application.
   *
   * @return a not {@code null} deployment descriptor
   */
  public AppEngineWebXml getAppEngineWebXml() {
    return appEngineWebXml;
  }

  /**
   * Returns the CronXml describing the applications' cron jobs.
   * @return a cron descriptor, possibly empty or {@code null}
   */
  public CronXml getCronXml() {
    return cronXml;
  }

  /**
   * Returns the QueueXml describing the applications' task queues.
   * @return a queue descriptor, possibly empty or {@code null}
   */
  public QueueXml getQueueXml() {
    return queueXml;
  }

  /**
   * Returns the DosXml describing the applications' DoS entries.
   * @return a dos descriptor, possibly empty or {@code null}
   */
  public DosXml getDosXml() {
    return dosXml;
  }

  /**
   * Returns the CronXml describing the applications' cron jobs.
   * @return a cron descriptor, possibly empty or {@code null}
   */
  public IndexesXml getIndexesXml() {
    return indexesXml;
  }

  /**
   * Returns the WebXml describing the applications' servlets and generic web
   * application information.
   *
   * @return a WebXml descriptor, possibly empty but not {@code null}
   */
  public WebXml getWebXml() {
    return webXml;
  }

  public BackendsXml getBackendsXml() {
    return backendsXml;
  }

  /**
   * Returns the desired API version for the current application, or
   * {@code "none"} if no API version was used.
   *
   * @throws IllegalStateException if createStagingDirectory has not been called.
   */
  String getApiVersion() {
    if (apiVersion == null) {
      throw new IllegalStateException("Must call createStagingDirectory first.");
    }
    return apiVersion;
  }

  /**
   * Returns a path to an exploded WAR directory for the application.
   * This may be a temporary directory.
   *
   * @return a not {@code null} path pointing to a directory
   */
  public String getPath() {
    return baseDir.getAbsolutePath();
  }

  /**
   * Returns the staging directory, or {@code null} if none has been created.
   */
  public File getStagingDir() {
    return stageDir;
  }

  void resetProgress() {
    updateProgress = 0;
    progressAmount = 0;
  }

  /**
   * Creates a new staging directory, if needed, or returns the existing one
   * if already created.
   *
   * @param opts User-specified options for processing the application.
   * @return staging directory
   * @throws IOException
   */
  File createStagingDirectory(ApplicationProcessingOptions opts)
      throws IOException {
    if (stageDir != null) {
      return stageDir;
    }

    int i = 0;
    while (stageDir == null && i++ < 3) {
      try {
        stageDir = File.createTempFile(STAGEDIR_PREFIX, null);
      } catch (IOException ex) {
        continue;
      }
      stageDir.delete();
      if (stageDir.mkdir() == false) {
        stageDir = null;
      }
    }
    if (i == 3) {
      throw new IOException("Couldn't create a temporary directory in 3 tries.");
    }
    statusUpdate("Created staging directory at: '" + stageDir.getPath() + "'", 20);

    File staticDir = new File(stageDir, "__static__");
    staticDir.mkdir();
    copyOrLink(baseDir, stageDir, staticDir, false, opts);

    if (opts.isCompileJspsSet()) {
      compileJsps(stageDir, opts);
    }

    apiVersion = findApiVersion(stageDir, true);

    if (opts.isSplitJarsSet()) {
      splitJars(new File(new File(stageDir, "WEB-INF"), "lib"),
                opts.getMaxJarSize(), opts.getJarSplittingExcludes());
    }

    checkFileSizes(stageDir, AppAdminFactory.MAX_FILE_UPLOAD);

    return stageDir;
  }

  private static String findApiVersion(File baseDir, boolean deleteApiJars) {
    ApiVersionFinder finder = new ApiVersionFinder();

    String foundApiVersion = null;
    File webInf = new File(baseDir, "WEB-INF");
    File libDir = new File(webInf, "lib");
    for (File file : new FileIterator(libDir)) {
      if (file.getPath().endsWith(".jar")) {
        try {
          String apiVersion = finder.findApiVersion(file);
          if (apiVersion != null) {
            if (foundApiVersion == null) {
              foundApiVersion = apiVersion;
            } else if (!foundApiVersion.equals(apiVersion)) {
              logger.warning("Warning: found duplicate API version: " + foundApiVersion +
                             ", using " + apiVersion);
            }
            if (deleteApiJars) {
              file.delete();
            }
          }
        } catch (IOException ex) {
          logger.log(Level.WARNING, "Could not identify API version in " + file, ex);
        }
      }
    }

    if (foundApiVersion == null) {
      foundApiVersion = "none";
    }
    return foundApiVersion;
  }

  /**
   * Validates a given XML document against a given schema.
   *
   * @param xmlFilename filename with XML document
   * @param schema XSD schema to validate with
   *
   * @throws AppEngineConfigException for malformed XML, or IO errors
   */
  private static void validateXml(String xmlFilename, File schema) {
    File xml = new File(xmlFilename);
    if (!xml.exists()) {
      return;
    }
    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      try {
        factory.newSchema(schema).newValidator().validate(
            new StreamSource(new FileInputStream(xml)));
      } catch (SAXException ex) {
        throw new AppEngineConfigException("XML error validating " +
            xml.getPath() + " against " + schema.getPath(), ex);
      }
    } catch (IOException ex) {
      throw new AppEngineConfigException("IO error validating " +
          xml.getPath() + " against " + schema.getPath(), ex);
    }
  }

  private static final String JSPC_MAIN = "com.google.appengine.tools.development.LocalJspC";

  private void compileJsps(File stage, ApplicationProcessingOptions opts)
      throws IOException {
    statusUpdate("Scanning for jsp files.");

    if (matchingFileExists(new File(stage.getPath()), JSP_REGEX)) {
      statusUpdate("Compiling jsp files.");

      File webInf = new File(stage, "WEB-INF");

      for (File file : SdkImplInfo.getUserJspLibFiles()) {
        copyOrLinkFile(file, new File(new File(webInf, "lib"), file.getName()));
      }
      for (File file : SdkImplInfo.getSharedJspLibFiles()) {
        copyOrLinkFile(file, new File(new File(webInf, "lib"), file.getName()));
      }

      File classes = new File(webInf, "classes");
      File generatedWebXml = new File(webInf, "generated_web.xml");
      String classpath = getJspClasspath(classes);

      String javaCmd = opts.getJavaExecutable().getPath();
      String[] args = new String[] {
        javaCmd,
        "-classpath", classpath,
        JSPC_MAIN,
        "-uriroot", stage.getPath(),
        "-p", "org.apache.jsp",
        "-l", "-v",
        "-webinc", generatedWebXml.getPath(),
        "-d", classes.getPath(),
        "-compile",
        "-javaEncoding", opts.getCompileEncoding(),
      };
      Process jspc = startProcess(args);

      int status = 1;
      try {
        status = jspc.waitFor();
      } catch (InterruptedException ex) { }

      if (status != 0) {
        detailsWriter.println("Error while executing: " + formatCommand(Arrays.asList(args)));
        throw new JspCompilationException("Failed to compile jsp files.",
                                          JspCompilationException.Source.JASPER);
      }

      webXml = new WebXmlReader(stage.getPath()).readWebXml();

    }
  }

  private String getJspClasspath(File classDir) {
    StringBuilder classpath = new StringBuilder();
    for (URL lib : SdkImplInfo.getImplLibs()) {
      classpath.append(lib.getPath());
      classpath.append(File.pathSeparatorChar);
    }
    for (File lib : SdkInfo.getSharedLibFiles()) {
      classpath.append(lib.getPath());
      classpath.append(File.pathSeparatorChar);
    }

    classpath.append(classDir.getPath());
    classpath.append(File.pathSeparatorChar);

    for (File f : new FileIterator(new File(classDir.getParentFile(), "lib"))) {
      String filename = f.getPath().toLowerCase();
      if (filename.endsWith(".jar") || filename.endsWith(".zip")) {
        classpath.append(f.getPath());
        classpath.append(File.pathSeparatorChar);
      }
    }

    return classpath.toString();
  }

  private Process startProcess(String... args) throws IOException {
    ProcessBuilder builder = new ProcessBuilder(args);
    Process proc = builder.redirectErrorStream(true).start();
    logger.fine(formatCommand(builder.command()));
    new Thread(new OutputPump(proc.getInputStream(), detailsWriter)).start();
    return proc;
  }

  private String formatCommand(Iterable<String> args) {
    StringBuilder command = new StringBuilder();
    for (String chunk : args) {
      command.append(chunk);
      command.append(" ");
    }
    return command.toString();
  }

  /**
   * Scans a given directory tree, testing whether any file matches a given
   * pattern.
   *
   * @param dir the directory under which to scan
   * @param regex the pattern to look for
   * @returns Returns {@code true} on the first instance of such a file,
   *   {@code false} otherwise.
   */
  private static boolean matchingFileExists(File dir, Pattern regex) {
    for (File file : dir.listFiles()) {
      if (file.isDirectory()) {
        if (matchingFileExists(file, regex)) {
          return true;
        }
      } else {
        if (regex.matcher(file.getName()).matches()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Scans the given directory, looking for files larger than {@code maxSize}.
   * Throws a {@link AppEngineConfigException} if any such files are found.
   *
   * @param dir the directory to search, recursively
   * @param max the maximum allowed size
   * @throws IllegalStateException if files are found that would not be
   *    eligible to be transfered.
   */
  private static void checkFileSizes(File dir, int max) {
    for (File file : dir.listFiles()) {
      if (file.isDirectory()) {
        checkFileSizes(file, max);
      } else {
        if (file.length() > max) {
          String message;
          if (file.getName().toLowerCase().endsWith(".jar")) {
            message = "Found a jar file too large to upload: \""
               + file.getPath() + "\".  Consider using --enable_jar_splitting.";
          } else {
            message = "Found a file too large to upload: \""
               + file.getPath() + "\".  Must be under " + max + " bytes.";
          }
          throw new IllegalStateException(message);
        }
      }
    }
  }

  /**
   * Invokes the JarSplitter code on any jar files found in {@code dir}.  Any
   * jars larger than {@code max} will be split into fragments of at most that
   * size.
   * @param dir the directory to search, recursively
   * @param max the maximum allowed size
   * @param excludes a set of suffixes to exclude.
   * @throws IOException on filesystem errors.
   */
  private static void splitJars(File dir, int max, Set<String> excludes) throws IOException {
    String children[] = dir.list();
    if (children == null) {
      return;
    }
    for (String name : children) {
      File subfile = new File(dir, name);
      if (subfile.isDirectory()) {
        splitJars(subfile, max, excludes);
      } else if (name.endsWith(".jar")) {
        if (subfile.length() > max) {
          new JarSplitter(subfile, dir, max, false, 4, excludes).run();
          subfile.delete();
        }
      }
    }
  }

  private static final Pattern SKIP_FILES = Pattern.compile(
      "^(.*/)?((#.*#)|(.*~)|(.*/RCS/.*)|)$");

  /**
   * Copies files from the app to the upload staging directory, or makes
   * symlinks instead if supported.  Puts the files into the correct places for
   * static vs. resource files, recursively.
   *
   * @param sourceDir application war dir, or on recursion a subdirectory of it
   * @param resDir staging resource dir, or on recursion a subdirectory matching
   *    the subdirectory in {@code sourceDir}
   * @param staticDir staging {@code __static__} dir, or an appropriate recursive
   *    subdirectory
   * @param forceResource if all files should be considered resource files
   * @param opts processing options, used primarily for handling of *.jsp files
   * @throws FileNotFoundException
   * @throws IOException
   */
  private void copyOrLink(File sourceDir, File resDir, File staticDir, boolean forceResource,
      ApplicationProcessingOptions opts)
    throws FileNotFoundException, IOException {

    for (String name : sourceDir.list()) {
      File file = new File(sourceDir, name);

      String path = file.getPath();
      if (File.separatorChar == '\\') {
        path = path.replace('\\', '/');
      }

      if (file.getName().startsWith(".") ||
          file.equals(GenerationDirectory.getGenerationDirectory(baseDir))) {
        continue;
      }

      if (file.isDirectory()) {
        if (file.getName().equals("WEB-INF")) {
          copyOrLink(file, new File(resDir, name), new File(staticDir, name), true, opts);
        } else {
          copyOrLink(file, new File(resDir, name), new File(staticDir, name), forceResource,
              opts);
        }
      } else {
        if (SKIP_FILES.matcher(path).matches()) {
          continue;
        }

        if (forceResource || appEngineWebXml.includesResource(path) ||
            (opts.isCompileJspsSet() && name.toLowerCase().endsWith(".jsp"))) {
          copyOrLinkFile(file, new File(resDir, name));
        }
        if (!forceResource && appEngineWebXml.includesStatic(path)) {
          copyOrLinkFile(file, new File(staticDir, name));
        }
      }
    }
  }

  /**
   * Attempts to symlink a single file, or copies it if symlinking is either
   * unsupported or fails.
   *
   * @param source source file
   * @param dest destination file
   * @throws FileNotFoundException
   * @throws IOException
   */
  private void copyOrLinkFile(File source, File dest)
      throws FileNotFoundException, IOException {
    dest.getParentFile().mkdirs();
    if (ln != null && !source.getName().endsWith("web.xml")) {
      Process link = startProcess(ln.getAbsolutePath(), "-s",
                                  source.getAbsolutePath(),
                                  dest.getAbsolutePath());
      try {
        int stat = link.waitFor();
        if (stat == 0) {
          return;
        }
        System.err.println(ln.getAbsolutePath() + " returned status " + stat
            + ", copying instead...");
      } catch (InterruptedException ex) {
        System.err.println(ln.getAbsolutePath() + " was interrupted, copying instead...");
      }
      if (dest.delete()) {
        System.err.println("ln failed but symlink was created, removed: " + dest.getAbsolutePath());
      }
    }
    byte buffer[] = new byte[1024];
    int readlen;
    FileInputStream inStream = new FileInputStream(source);
    FileOutputStream outStream = new FileOutputStream(dest);
    try {
      readlen = inStream.read(buffer);
      while (readlen > 0) {
        outStream.write(buffer, 0, readlen);
        readlen = inStream.read(buffer);
      }
    } finally {
      try {
        inStream.close();
      } catch (IOException ex) {
      }
      try {
        outStream.close();
      } catch (IOException ex) {
      }
    }
  }

  /** deletes the staging directory, if one was created. */
  public void cleanStagingDirectory() {
    if (stageDir != null) {
      recursiveDelete(stageDir);
    }
  }

  /** Recursive directory deletion. */
  public static void recursiveDelete(File dead) {
    String[] files = dead.list();
    if (files != null) {
      for (String name : files) {
        recursiveDelete(new File(dead, name));
      }
    }
    dead.delete();
  }

  void setListener(UpdateListener l) {
    listener = l;
  }

  void setDetailsWriter(PrintWriter detailsWriter) {
    this.detailsWriter = detailsWriter;
  }

  public void statusUpdate(String message, int amount) {
    updateProgress += progressAmount;
    if (updateProgress > 99) {
      updateProgress = 99;
    }
    progressAmount = amount;
    if (listener != null) {
      listener.onProgress(new UpdateProgressEvent(
                              Thread.currentThread(), message, updateProgress));
    }
  }

  public void statusUpdate(String message) {
    int amount = progressAmount / 4;
    updateProgress += amount;
    if (updateProgress > 99) {
      updateProgress = 99;
    }
    progressAmount -= amount;
    if (listener != null) {
      listener.onProgress(new UpdateProgressEvent(
                              Thread.currentThread(), message, updateProgress));
    }
  }
}
