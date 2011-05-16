// Copyright 2009 Google Inc. All Rights Reserved.
package com.google.appengine.tools.development.testing;

import com.google.appengine.tools.development.LocalServerEnvironment;

import java.io.File;

/**
 * {@link LocalServerEnvironment} implementation used for local service tests.
 *
*/
class TestLocalServerEnvironment implements LocalServerEnvironment {

  private final boolean enforceApiDeadlines;

  TestLocalServerEnvironment(boolean enforceApiDeadlines) {
    this.enforceApiDeadlines = enforceApiDeadlines;
  }

  @Override
  public File getAppDir() {
    return new File(".");
  }

  @Override
  public String getAddress() {
    return "localhost";
  }

  @Override
  public int getPort() {
    return 8080;
  }

  @Override
  public void waitForServerToStart() {
  }

  @Override
  public boolean enforceApiDeadlines() {
    return enforceApiDeadlines;
  }
}
