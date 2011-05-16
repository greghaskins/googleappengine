// Copyright 2007 Google Inc. All rights reserved.

package com.google.appengine.api.users;

/**
 * Creates a UserService.
 *
 */
public final class UserServiceFactory {
  /**
   * Creates an implementation of the UserService.
   */
  public static UserService getUserService() {
    return new UserServiceImpl();
  }

  private UserServiceFactory() {
  }
}
