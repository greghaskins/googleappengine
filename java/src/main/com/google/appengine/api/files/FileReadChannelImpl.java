// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.api.files;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;

/**
 * An implementation of {@code FileReadChannel}.
 *
 */
class FileReadChannelImpl implements FileReadChannel {

  private FileServiceImpl fileService;
  private AppEngineFile file;
  private long position;
  private boolean isOpen;

  FileReadChannelImpl(AppEngineFile f, FileServiceImpl fs) {
    this.file = f;
    this.fileService = fs;
    isOpen = true;
    if (null == file) {
      throw new NullPointerException("file is null");
    }
    if (null == fs) {
      throw new NullPointerException("fs is null");
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
  public long position() throws IOException {
    checkOpen();
    return position;
  }

  /**
   * {@inheritDoc}
   */
  public FileReadChannel position(long newPosition) throws IOException {
    if (newPosition < 0) {
      throw new IllegalArgumentException("newPosition may not be negative");
    }
    checkOpen();
    position = newPosition;
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public int read(ByteBuffer dst) throws IOException {
    if (position < 0) {
      return -1;
    }
    int numBytesRead = fileService.read(file, dst, position);
    if (numBytesRead >= 0) {
      position += numBytesRead;
    } else {
      position = -1;
    }
    return numBytesRead;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isOpen() {
    return false;
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
}
