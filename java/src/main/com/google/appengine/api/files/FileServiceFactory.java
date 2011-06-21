// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.api.files;

/**
 * A factory for producing instances of {@link FileService}.
 *
 */
public class FileServiceFactory {
  /**
   * Returns an instance of {@link FileService}.
   */
  public static FileService getFileService() {
    return new FileServiceImpl();
  }
}
