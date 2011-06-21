// Copyright 2011 Google Inc. All rights reserved.
package com.google.appengine.api.appidentity;

/**
 * Creates new instances of the App identity service.
 *
 */
public final class AppIdentityServiceFactory {
  private AppIdentityServiceFactory() {}

  public static AppIdentityService getAppIdentityService() {
    return new AppIdentityServiceImpl();
  }
}
