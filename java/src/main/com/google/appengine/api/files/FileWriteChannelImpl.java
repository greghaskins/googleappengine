// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.api.files;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;

/**
 * An implementation of {@code FileWriteChannel}.
 *
 */
class FileWriteChannelImpl implements FileWriteChannel {

  private FileServiceImpl fileService;
  private AppEngineFile file;
  private boolean lockHeld;
  private boolean isOpen;

  FileWriteChannelImpl(AppEngineFile f, boolean lock, FileServiceImpl fs) {
    this.file = f;
    this.lockHeld = lock;
    this.fileService = fs;
    isOpen = true;
    if (null == file) {
      throw new NullPointerException("file is null");
    }
  }

  private void checkOpen() throws ClosedChannelException {
    if (!isOpen) {
      throw new ClosedChannelException();
    }
  }

  /**
   * {@inheritDoc}
   */
  public int write(ByteBuffer src) throws IOException {
    return write(src, null);
  }

  /**
   * {@inheritDoc}
   */
  public int write(ByteBuffer buffer, String sequenceKey) throws IOException {
    checkOpen();
    return fileService.append(file, buffer, sequenceKey);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isOpen() {
    return isOpen;
  }

  /**
   * {@inheritDoc}
   */
  public void close() throws IOException {
    if (!isOpen) {
      return;
    }
    fileService.close(file, false);
    isOpen = false;
  }

  /**
   * {@inheritDoc}
   */
  public void closeFinally() throws IllegalStateException, IOException {
    if (!lockHeld) {
      throw new IllegalStateException("The lock for this file is not held by the current request");
    }
    if (isOpen) {
      fileService.close(file, true);
    } else {
      try {
        fileService.openForAppend(file, true);
        fileService.close(file, true);
      } catch (FinalizationException e) {
      }
    }
    isOpen = false;
  }

}
