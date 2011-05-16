// Copyright 2007 Google Inc. All Rights Reserved.

package com.google.appengine.tools.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Logger;

/**
 * Simple utility that splits a large jar file into one or more jar
 * files that are each less than the file size specified with
 * --max_file_size.
 *
 * This class strips out jar index files.  It does not, however,
 * ensure that resource or META-INF files are directed to the
 * appropriate jar file.  It's unclear whether this will cause
 * problems or not.
 *
 * UPDATE to the lack of clarity:  It is now clear that this will
 * cause problems.  Some frameworks (datanucleus in particular)
 * make assumptions about the colocation of well-known files
 * and the manifest in the same jar.  Splitting the jar
 * violates these assumptions.
 *
 */
public class JarSplitter {
  private static final String EXT = ".jar";
  private static final String INDEX_FILE = "INDEX.LIST";

  private static final int READ_BUFFER_SIZE_BYTES = 8 * 1024;
  private static final int FILE_BUFFER_INITIAL_SIZE_BYTES = 512 * 1024;

  private static Logger logger = Logger.getLogger(JarSplitter.class.getName());

  private final File inputJar;
  private final File outputDirectory;
  private final int maximumSize;
  private final boolean replicateManifests;
  private final Set<String> excludes;

  private int nextFileIndex = 0;
  private long currentSize = 0L;
  private JarOutputStream currentStream;
  private int outputDigits;

  public JarSplitter(File inputJar, File outputDirectory, int maximumSize,
                     boolean replicateManifests, int outputDigits, Set<String> excludes) {
    this.inputJar = inputJar;
    this.outputDirectory = outputDirectory;
    this.maximumSize = maximumSize;
    this.replicateManifests = replicateManifests;
    this.outputDigits = outputDigits;
    this.excludes = excludes;
  }

  public void run() throws IOException {
    outputDirectory.mkdirs();

    JarInputStream inputStream = new JarInputStream(
        new BufferedInputStream(new FileInputStream(inputJar)));

    Manifest manifest = inputStream.getManifest();
    long manifestSize = 0;
    if (manifest != null && replicateManifests) {
      manifestSize = getManifestSize(manifest);
    }
    currentStream = newJarOutputStream(manifest);
    currentSize = manifestSize;

    byte[] readBuffer = new byte[READ_BUFFER_SIZE_BYTES];
    ByteArrayOutputStream fileBuffer = new ByteArrayOutputStream(FILE_BUFFER_INITIAL_SIZE_BYTES);
    JarEntry entry;
    while ((entry = inputStream.getNextJarEntry()) != null) {
      String name = entry.getName();

      if (shouldIncludeFile(name)) {
        JarEntry newEntry = new JarEntry(entry.getName());
        newEntry.setTime(entry.getTime());

        fileBuffer.reset();
        readIntoBuffer(inputStream, readBuffer, fileBuffer);
        long size = fileBuffer.size();
        if ((currentSize + size) >= maximumSize) {
          logger.info("Closing file after writing " + currentSize + " bytes.");
          beginNewOutputStream(manifest, manifestSize);
        }

        logger.fine("Copying entry: " + name + " (" + size + " bytes)");
        currentStream.putNextEntry(newEntry);
        fileBuffer.writeTo(currentStream);
        currentSize += size;
      }
    }

    inputStream.close();

    logger.info("Closing file after writing " + currentSize + " bytes.");
    currentStream.close();
  }

  private boolean shouldIncludeFile(String fileName) {
    if (fileName.endsWith(INDEX_FILE)) {
      logger.info("Skipping jar index file: " + fileName);
      return false;
    }

    for (String suffix : excludes) {
      if (fileName.endsWith(suffix)) {
        logger.info("Skipping file matching excluded suffix '" + suffix + "': " + fileName);
        return false;
      }
    }

    return true;
  }

  private long getManifestSize(Manifest manifest) throws IOException{
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      manifest.write(baos);
      return baos.size();
    } finally {
      baos.close();
    }
  }

  private JarOutputStream newJarOutputStream(Manifest manifest) throws IOException {
    if (manifest == null || !replicateManifests) {
      return new JarOutputStream(createOutFile(nextFileIndex++));
    }
    return new JarOutputStream(createOutFile(nextFileIndex++), manifest);
  }

  /**
   * Close the current output stream and open a new one with the next
   * available index number.
   */
  private void beginNewOutputStream(Manifest manifest, long manifestSize) throws IOException {
    currentStream.close();
    currentStream = newJarOutputStream(manifest);
    currentSize = manifestSize;
  }

  private void readIntoBuffer(InputStream inputStream, byte[] readBuffer, ByteArrayOutputStream out)
      throws IOException {
    int count;
    while ((count = inputStream.read(readBuffer)) != -1) {
      out.write(readBuffer, 0, count);
    }
  }

  private OutputStream createOutFile(int index) throws IOException {
    String name = inputJar.getName();
    if (name.endsWith(EXT)) {
      name = name.substring(0, name.length() - EXT.length());
    }

    String formatString = "%s-%0" + outputDigits + "d%s";
    String newName = String.format(formatString, name, index, EXT);
    File newFile = new File(outputDirectory, newName);
    logger.info("Opening new file: " + newFile);
    return new BufferedOutputStream(new FileOutputStream(newFile));
  }
}
