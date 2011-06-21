// Copyright 2010 Google Inc. All rights reserved.

package com.google.appengine.api.oauth;

/**
 * Creates an OAuthService.
 *
 */
public final class OAuthServiceFactory {
  /**
   * Creates an implementation of the OAuthService.
   */
  public static OAuthService getOAuthService() {
    return new OAuthServiceImpl();
  }

  private OAuthServiceFactory() {
  }
}
