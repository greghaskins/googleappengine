// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.api.backends;

/**
 * Factory for getting the Backends API implementation for the current
 * environment.
 *
 */
public class BackendServiceFactory {

  private BackendServiceFactory() {
  }

  /**
   * Gets a handle to the backends API for the current running environment.
   *
   * @return An implementation of the backends API.
   */
  public static BackendService getBackendService() {
    return new BackendServiceImpl();
  }
}
