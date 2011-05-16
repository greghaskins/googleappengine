// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.tools.admin;

import com.google.appengine.tools.admin.AppAdminFactory.ConnectOptions;

/**
 * Factory for retrieving a ServerConnection instance. Returns either a
 * ClientLogin or OAuth2-based implementation.
 *
 */
public final class ServerConnectionFactory {

  /**
   * Get a new {@link ServerConnection} instance.
   */
  public static ServerConnection getServerConnection(ConnectOptions options) {
    if (options.getOauthToken() != null) {
      return new OAuth2ServerConnection(options);
    }
    return new ClientLoginServerConnection(options);
  }

  private ServerConnectionFactory() {
  }
}
