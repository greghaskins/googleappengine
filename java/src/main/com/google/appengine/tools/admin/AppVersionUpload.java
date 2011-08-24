// Copyright 2009 Google Inc. All rights reserved.

package com.google.appengine.tools.admin;

import com.google.appengine.tools.admin.Application.ErrorHandler;
import com.google.appengine.tools.util.FileIterator;
import com.google.common.base.Join;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Uploads a new appversion to the hosting service.
 *
 */
public class AppVersionUpload {
  /**
   * Max number of files per application, should match the limit server-side.
   */
  private static final int MAX_FILE_COUNT = 3000;
  private static final long MB = 1000000;
  /**
   * Max size of an individual file, should match server-side limit.
   */
  private static final long MAX_FILE_SIZE = 10 * MB;
  /**
   * Max total size of resource files, should match server-side limit.
   */
  private static final long MAX_RESOURCE_TOTALSIZE = 150 * MB;

  /**
   * Don't try to precompile more than this number of files in one request.
   */
  private static final int MAX_FILES_PER_PRECOMPILE = 50;

  protected ServerConnection connection;
  protected Application app;
  protected final String backend;
  private Logger logger = Logger.getLogger(AppVersionUpload.class.getName());
  private boolean inTransaction = false;
  private Map<String, FileInfo> files = new HashMap<String, FileInfo>();
  private boolean deployed = false;

  public AppVersionUpload(ServerConnection connection, Application app) {
    this(connection, app, null);
  }

  /**
   * Create a new {@link AppVersionUpload} instance that can deploy a new
   * versions of {@code app} via {@code connection}.
   *
   * @param connection to connect to the server
   * @param app that contains the code to be deployed
   * @param backend if supplied and non-{@code null}, a particular backend is
   *        being updated
   */
  public AppVersionUpload(ServerConnection connection, Application app,
      String backend) {
    this.connection = connection;
    this.app = app;
    this.backend = backend;
  }

  /***
   * Uploads a new appversion to the server.
   *
   * @throws IOException if a problem occurs in the upload.
   */
  public void doUpload() throws IOException {
    try {
      File basepath = getBasepath();

      app.statusUpdate("Scanning files on local disk.", 20);
      int numFiles = 0;
      long resourceTotal = 0;
      for (File f : new FileIterator(basepath)) {
        logger.fine("Processing file '" + f + "'.");
        if (f.length() > MAX_FILE_SIZE) {
          throw new IOException("File " + f.getPath() + " is too large (limit "
              + MAX_FILE_SIZE + " bytes).");
        }
        resourceTotal += addFile(f, basepath);

        if (++numFiles % 250 == 0) {
          app.statusUpdate("Scanned " + numFiles + " files.");
        }
      }
      if (numFiles > MAX_FILE_COUNT) {
        throw new IOException("Applications are limited to " + MAX_FILE_COUNT
            + " files, you have " + numFiles + ".");
      }
      if (resourceTotal > MAX_RESOURCE_TOTALSIZE) {
        throw new IOException("Applications are limited to "
            + MAX_RESOURCE_TOTALSIZE + " bytes of resource files, you have "
            + resourceTotal + ".");
      }

      Collection<FileInfo> missingFiles = beginTransaction();
      app.statusUpdate("Uploading " + missingFiles.size() + " files.", 50);
      if (missingFiles.size() > 0) {
        numFiles = 0;
        int quarter = Math.max(1, missingFiles.size() / 4);
        for (FileInfo missingFile : missingFiles) {
          logger.fine("Uploading file '" + missingFile + "'");
          uploadFile(missingFile);
          if (++numFiles % quarter == 0) {
            app.statusUpdate("Uploaded " + numFiles + " files.");
          }
        }
      }
      uploadErrorHandlers(app.getErrorHandlers(), basepath);
      if (app.isPrecompilationEnabled()) {
        precompile();
      }
      commit();
    } finally {
      rollback();
    }

    updateIndexes();
    updateCron();
    updateQueue();
    updateDos();
  }

  private void uploadErrorHandlers(List<ErrorHandler> errorHandlers, File basepath)
    throws IOException {
    if (!errorHandlers.isEmpty()) {
      app.statusUpdate("Uploading " + errorHandlers.size() + " file(s) "
          + "for static error handlers.");
      for (ErrorHandler handler : errorHandlers) {
        File file = new File(basepath, handler.getFile());
        FileInfo info = new FileInfo(file, basepath);
        String error = info.checkValidFilename();
        if (error != null) {
          throw new IOException("Could not find static error handler: " + error);
        }
        info.mimeType = handler.getMimeType();
        String errorType = handler.getErrorCode();
        if (errorType == null) {
          errorType = "default";
        }
        send("/api/appversion/adderrorblob", info.file, info.mimeType, "path",
            errorType);
      }
    }
  }

  public void precompile() throws IOException {
    app.statusUpdate("Initializing precompilation...");
    List<String> filesToCompile = new ArrayList<String>();

    int errorCount = 0;
    while (true) {
      try {
        filesToCompile.addAll(sendPrecompileRequest(Collections
            .<String> emptyList()));
        break;
      } catch (IOException ex) {
        if (errorCount < 3) {
          errorCount++;
          try {
            Thread.sleep(1000);
          } catch (InterruptedException ex2) {
            IOException ex3 =
                new IOException("Interrupted during precompilation.");
            ex3.initCause(ex2);
            throw ex3;
          }
        } else {
          IOException ex2 =
              new IOException(
                  "Precompilation failed.  Consider adding <precompilation-enabled>false"
                      + "</precompilation-enabled> to your appengine-web.xml and trying again.");
          ex2.initCause(ex);
          throw ex2;
        }
      }
    }

    errorCount = 0;
    IOException lastError = null;
    while (!filesToCompile.isEmpty()) {
      try {
        if (precompileChunk(filesToCompile)) {
          errorCount = 0;
        }
      } catch (IOException ex) {
        lastError = ex;
        errorCount++;
        Collections.shuffle(filesToCompile);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ex2) {
          IOException ex3 =
              new IOException("Interrupted during precompilation.");
          ex3.initCause(ex2);
          throw ex3;
        }
      }

      if (errorCount > 3) {
        IOException ex2 =
            new IOException("Precompilation failed with "
                + filesToCompile.size() + " file(s) remaining.  "
                + "Consider adding"
                + " <precompilation-enabled>false</precompilation-enabled>"
                + " to your " + "appengine-web.xml and trying again.");
        ex2.initCause(lastError);
        throw ex2;
      }
    }
  }

  /**
   * Attempt to precompile up to {@code MAX_FILES_PER_PRECOMPILE} files from
   * {@code filesToCompile}.
   *
   * @param filesToCompile a list of file names, which will be mutated to remove
   *        any files that were successfully compiled.
   *
   * @return true if filesToCompile was reduced in size (i.e. progress was
   *         made).
   */
  private boolean precompileChunk(List<String> filesToCompile)
      throws IOException {
    int filesLeft = filesToCompile.size();
    if (filesLeft == 0) {
      app.statusUpdate("Initializing precompilation...");
    } else {
      app.statusUpdate(MessageFormat.format(
          "Precompiling... {0} file(s) left.", filesLeft));
    }

    List<String> subset =
        filesToCompile
            .subList(0, Math.min(filesLeft, MAX_FILES_PER_PRECOMPILE));
    List<String> remainingFiles = sendPrecompileRequest(subset);
    subset.clear();
    filesToCompile.addAll(remainingFiles);
    return filesToCompile.size() < filesLeft;
  }

  private List<String> sendPrecompileRequest(List<String> filesToCompile)
      throws IOException {
    String response =
        send("/api/appversion/precompile", Join.join("\n", filesToCompile));
    if (response.length() > 0) {
      return Arrays.asList(response.split("\n"));
    } else {
      return Collections.emptyList();
    }
  }

  public void updateIndexes() throws IOException {
    if (app.getIndexesXml() != null) {
      app.statusUpdate("Uploading index definitions.");
      send("/api/datastore/index/add", getIndexYaml());
    }

  }

  public void updateCron() throws IOException {
    String yaml = getCronYaml();
    if (yaml != null) {
      app.statusUpdate("Uploading cron jobs.");
      send("/api/datastore/cron/update", yaml);
    }
  }

  public void updateQueue() throws IOException {
    String yaml = getQueueYaml();
    if (yaml != null) {
      app.statusUpdate("Uploading task queues.");
      send("/api/queue/update", yaml);
    }
  }

  public void updateDos() throws IOException {
    String yaml = getDosYaml();
    if (yaml != null) {
      app.statusUpdate("Uploading DoS entries.");
      send("/api/dos/update", yaml);
    }
  }

  protected String getIndexYaml() {
    return app.getIndexesXml().toYaml();
  }

  protected String getCronYaml() {
    if (app.getCronXml() != null) {
      return app.getCronXml().toYaml();
    } else {
      return null;
    }
  }

  protected String getQueueYaml() {
    if (app.getQueueXml() != null) {
      return app.getQueueXml().toYaml();
    } else {
      return null;
    }
  }

  protected String getDosYaml() {
    if (app.getDosXml() != null) {
      return app.getDosXml().toYaml();
    } else {
      return null;
    }
  }

  private File getBasepath() {
    File path = app.getStagingDir();
    if (path == null) {
      path = new File(app.getPath());
    }
    return path;
  }

  /**
   * Adds a file for uploading, returning the bytes counted against the total
   * resource quota.
   *
   * @param file
   * @param base
   * @return 0 for a static file, or file.length() for a resource file.
   * @throws IOException
   */
  private long addFile(File file, File base) throws IOException {
    long returnBytes = file.length();
    if (inTransaction) {
      throw new IllegalStateException("Already in a transaction.");
    }

    FileInfo info = new FileInfo(file, base);
    String error = info.checkValidFilename();
    if (error != null) {
      logger.severe(error);
      return 0;
    }

    info.mimeType = app.getMimeTypeIfStatic(info.path);
    if (info.mimeType != null) {
      returnBytes = 0;
    }
    files.put(info.path, info);
    return returnBytes;
  }

  /**
   * Begins the transaction, returning a list of files that need uploading.
   *
   * All calls to addFile must be made before calling beginTransaction().
   *
   * @return A list of pathnames that should be uploaded using uploadFile()
   *         before calling commit().
   */
  private Collection<FileInfo> beginTransaction() throws IOException {
    if (inTransaction) {
      throw new IllegalStateException("Already in a transaction.");
    }

    if (backend == null) {
      app.statusUpdate("Initiating update.");
    } else {
      app.statusUpdate("Initiating update of backend " + backend + ".");
    }
    send("/api/appversion/create", app.getAppYaml());
    inTransaction = true;
    Collection<FileInfo> blobsToClone = new ArrayList<FileInfo>(files.size());
    Collection<FileInfo> filesToClone = new ArrayList<FileInfo>(files.size());

    for (FileInfo f : files.values()) {
      if (f.mimeType == null) {
        filesToClone.add(f);
      } else {
        blobsToClone.add(f);
      }
    }

    TreeMap<String, FileInfo> filesToUpload = new TreeMap<String, FileInfo>();
    cloneFiles("/api/appversion/cloneblobs", blobsToClone, "static",
        filesToUpload);
    cloneFiles("/api/appversion/clonefiles", filesToClone, "application",
        filesToUpload);

    logger.fine("Files to upload :");
    for (FileInfo f : filesToUpload.values()) {
      logger.fine("\t" + f);
    }

    this.files = filesToUpload;
    return new ArrayList<FileInfo>(filesToUpload.values());
  }

  private static final int MAX_FILES_TO_CLONE = 100;
  private static final String LIST_DELIMITER = "\n";

  /**
   * Sends files to the given url.
   *
   * @param url server URL to use.
   * @param filesParam List of files to clone.
   * @param type Type of files ( "static" or "application")
   * @param filesToUpload Files that need to be uploaded are added to this
   *        Collection.
   */
  private void cloneFiles(String url, Collection<FileInfo> filesParam,
      String type, Map<String, FileInfo> filesToUpload) throws IOException {
    if (filesParam.isEmpty()) {
      return;
    }
    app.statusUpdate("Cloning " + filesParam.size() + " " + type + " files.");

    int cloned = 0;
    int remaining = filesParam.size();
    ArrayList<FileInfo> chunk = new ArrayList<FileInfo>(MAX_FILES_TO_CLONE);
    for (FileInfo file : filesParam) {
      chunk.add(file);
      if (--remaining == 0 || chunk.size() >= MAX_FILES_TO_CLONE) {
        if (cloned > 0) {
          app.statusUpdate("Cloned " + cloned + " files.");
        }
        String result = send(url, buildClonePayload(chunk));
        if (result != null && result.length() > 0) {
          for (String path : result.split(LIST_DELIMITER)) {
            if (path == null || path.length() == 0) {
              continue;
            }
            FileInfo info = this.files.get(path);
            if (info == null) {
              logger.warning("Skipping " + path + ": missing FileInfo");
              continue;
            }
            filesToUpload.put(path, info);
          }
        }
        cloned += chunk.size();
        chunk.clear();
      }
    }
  }

  /**
   * Uploads a file to the hosting service.
   *
   * Must only be called after beginTransaction(). The file provided must be on
   * of those that were returned by beginTransaction();
   *
   * @param file FileInfo for the file to upload.
   */
  private void uploadFile(FileInfo file) throws IOException {
    if (!inTransaction) {
      throw new IllegalStateException(
          "beginTransaction() must be called before uploadFile().");
    }
    if (!files.containsKey(file.path)) {
      throw new IllegalArgumentException("File " + file.path
          + " is not in the list of files to be uploaded.");
    }

    files.remove(file.path);
    if (file.mimeType == null) {
      send("/api/appversion/addfile", file.file, null, "path", file.path);
    } else {
      send("/api/appversion/addblob", file.file, file.mimeType, "path",
          file.path);
    }
  }

  /**
   * Commits the transaction, making the new app version available.
   *
   * All the files returned by beginTransaction must have been uploaded with
   * uploadFile() before commit() may be called.
   */
  private void commit() throws IOException {
    deploy();
    try {
      boolean ready = retryWithBackoff(1, 2, 60, 20, new Callable<Boolean>() {
        public Boolean call() throws Exception {
          return isReady();
        }
      });

      if (ready) {
        startServing();
      } else {
        logger.severe("Version still not ready to serve, aborting.");
        throw new RuntimeException("Version not ready.");
      }
    } catch (IOException ioe) {
      throw ioe;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Deploys the new app version but does not make it default.
   *
   * All the files returned by beginTransaction must have been uploaded with
   * uploadFile() before commit() may be called.
   */
  private void deploy() throws IOException {
    if (!inTransaction) {
      throw new IllegalStateException(
          "beginTransaction() must be called before uploadFile().");
    }
    if (files.size() > 0) {
      throw new IllegalStateException(
          "Some required files have not been uploaded.");
    }
    app.statusUpdate("Deploying new version.", 20);
    send("/api/appversion/deploy", "");
    deployed = true;
  }

  /**
   * Check if the new app version is ready to serve traffic.
   *
   * @return true if the server returned that the app is ready to serve.
   */
  private boolean isReady() throws IOException {
    if (!deployed) {
      throw new IllegalStateException(
          "deploy() must be called before isReady()");
    }
    String result = send("/api/appversion/isready", "");
    return "1".equals(result.trim());
  }

  private void startServing() throws IOException {
    if (!deployed) {
      throw new IllegalStateException(
          "deploy() must be called before startServing()");
    }
    app.statusUpdate("Closing update: new version is ready to start serving.");
    send("/api/appversion/startserving", "");
    inTransaction = false;
  }

  public void forceRollback() throws IOException {
    app.statusUpdate("Rolling back the update" + this.backend == null ? "."
        : " on backend " + this.backend + ".");
    send("/api/appversion/rollback", "");
  }

  private void rollback() throws IOException {
    if (!inTransaction) {
      return;
    }
    forceRollback();
  }

  private String send(String url, String payload, String... args)
      throws IOException {
    return connection.post(url, payload, addVersionToArgs(args));
  }

  private String send(String url, File payload, String mimeType, String... args)
      throws IOException {
    return connection.post(url, payload, mimeType, addVersionToArgs(args));
  }

  private String[] addVersionToArgs(String... args) {
    List<String> result = new ArrayList<String>();
    result.addAll(Arrays.asList(args));
    result.add("app_id");
    result.add(app.getAppId());
    if (backend != null) {
      result.add("backend");
      result.add(backend);
    } else if (app.getVersion() != null) {
      result.add("version");
      result.add(app.getVersion());
    }
    return result.toArray(new String[result.size()]);
  }

  /**
   * Calls a function multiple times, backing off more and more each time.
   *
   * @param initialDelay Inital delay after the first try, in seconds.
   * @param backoffFactor Delay will be multiplied by this factor after each
   *        try.
   * @param maxDelay Maximum delay factor.
   * @param maxTries Maximum number of tries.
   * @param callable Callable to call.
   * @return true if the Callable returned true in one of its tries.
   */
  private boolean retryWithBackoff(double initialDelay, double backoffFactor,
      double maxDelay, int maxTries, Callable<Boolean> callable)
      throws Exception {
    long delayMillis = (long) (initialDelay * 1000);
    long maxDelayMillis = (long) (maxDelay * 1000);
    if (callable.call()) {
      return true;
    }
    while (maxTries > 1) {
      app.statusUpdate("Will check again in " + (delayMillis / 1000)
          + " seconds.");
      Thread.sleep(delayMillis);
      delayMillis *= backoffFactor;
      if (delayMillis > maxDelayMillis) {
        delayMillis = maxDelayMillis;
      }
      maxTries--;
      if (callable.call()) {
        return true;
      }
    }
    return false;
  }

  private static final String TUPLE_DELIMITER = "|";

  /**
   * Build the post body for a clone request.
   *
   * @param files List of FileInfos for the files to clone.
   * @return A string containing the properly delimited tuples.
   */
  private static String buildClonePayload(Collection<FileInfo> files) {
    StringBuffer data = new StringBuffer();
    boolean first = true;
    for (FileInfo file : files) {
      if (first) {
        first = false;
      } else {
        data.append(LIST_DELIMITER);
      }
      data.append(file.path);
      data.append(TUPLE_DELIMITER);
      data.append(file.hash);
      if (file.mimeType != null) {
        data.append(TUPLE_DELIMITER);
        data.append(file.mimeType);
      }
    }

    return data.toString();
  }

  private static class FileInfo implements Comparable<FileInfo> {
    public File file;
    public String path;
    public String hash;
    public String mimeType;

    public FileInfo(File f, File base) throws IOException {
      this.file = f;
      this.path = Utility.calculatePath(f, base);
      this.hash = calculateHash();
    }

    @Override
    public String toString() {
      return (mimeType == null ? "" : mimeType) + '\t' + hash + "\t" + path;
    }

    public int compareTo(FileInfo other) {
      return path.compareTo(other.path);
    }

    @Override
    public int hashCode() {
      return path.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof FileInfo) {
        return path.equals(((FileInfo) obj).path);
      }
      return false;
    }

    private static final Pattern FILE_PATH_POSITIVE_RE =
        Pattern.compile("^[ 0-9a-zA-Z._+/$-]{1,256}$");

    private static final Pattern FILE_PATH_NEGATIVE_RE_1 =
        Pattern.compile("[.][.]|^[.]/|[.]$|/[.]/|^-");

    private static final Pattern FILE_PATH_NEGATIVE_RE_2 =
        Pattern.compile("//|/$");

    private static final Pattern FILE_PATH_NEGATIVE_RE_3 =
        Pattern.compile("^ | $|/ | /");

    private String checkValidFilename() {
      if (!FILE_PATH_POSITIVE_RE.matcher(path).matches()) {
        return "Invalid character in filename: " + path;
      }
      if (FILE_PATH_NEGATIVE_RE_1.matcher(path).find()) {
        return "Filname cannot contain '.' or '..' or start with '-': " + path;
      }
      if (FILE_PATH_NEGATIVE_RE_2.matcher(path).find()) {
        return "Filname cannot have trailing / or contain //: " + path;
      }
      if (FILE_PATH_NEGATIVE_RE_3.matcher(path).find()) {
        return "Any spaces must be in the middle of a filename: '" + path + "'";
      }
      return null;
    }

    private static final char[] HEX =
        {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',
            'e', 'f'};

    public String calculateHash() throws IOException {
      InputStream s = new FileInputStream(file);
      byte[] buf = new byte[4096];
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        for (int numRead; (numRead = s.read(buf)) != -1;) {
          digest.update(buf, 0, numRead);
        }
        StringBuffer hashValue = new StringBuffer(40);
        int i = 0;
        for (byte b : digest.digest()) {
          if ((i > 0) && ((i % 4) == 0)) {
            hashValue.append('_');
          }
          hashValue.append(HEX[(b >> 4) & 0xf]);
          hashValue.append(HEX[b & 0xf]);
          ++i;
        }

        return hashValue.toString();
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      } finally {
        try {
          s.close();
        } catch (IOException ex) {
          ;
        }
      }
    }
  }
}
