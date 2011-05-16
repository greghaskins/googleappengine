// Copyright 2009, Google Inc.  All rights reserved.

package com.google.appengine.tools.util;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * Walks a directory tree, returning all the files.
 *
 *
 */
public class FileIterator implements Iterator<File>, Iterable<File> {
  private LinkedList<File> dirs = new LinkedList<File>();
  private Iterator<File> files;
  private File next;

  public FileIterator(File base) {
    File[] baseFiles = base.listFiles();
    if (baseFiles != null) {
      files = Arrays.asList(baseFiles).iterator();
    }
    _next();
  }

  @Override
  public boolean hasNext() {
    return next != null;
  }

  @Override
  public File next() {
    if (next == null) {
      throw new NoSuchElementException();
    }
    return _next();
  }

  private File _next() {
    File result = next;
    next = null;
    while (files != null) {
      try {
        File f = files.next();
        if (f.isDirectory()) {
          dirs.add(f);
        } else {
          next = f;
          break;
        }
      } catch (NoSuchElementException ex) {
        if (dirs.isEmpty())
          files = null;
        else
          files = Arrays.asList(dirs.removeFirst().listFiles()).iterator();
      }
    }
    return result;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  public Iterator<File> iterator() {
    return this;
  }
}
