// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.tools.remoteapi;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.util.EncodingUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Abstract class that handles making HTTP requests to App Engine.  The actual
 * mechanism by which the HTTP requests are made is left to subclasses to
 * implement.
 * <p> This class is thread-safe. </p>
 *
 */
abstract class AppEngineClient {
  final String hostname;
  private final int port;
  private final String userEmail;
  final Cookie[] authCookies;
  private final String remoteApiPath;
  final int maxResponseSize;

  private final String appId;

  static AppEngineClient newInstance(RemoteApiOptions options, List<Cookie> authCookies, String appId) {
    if (options.isAppEngineContainer()) {
      return new AppEngineUrlFetchClient(options, authCookies, appId);
    }
    return new AppEngineHttpClient(options, authCookies, appId);
  }

  AppEngineClient(RemoteApiOptions options,
      List<Cookie> authCookies, String appId) {
    if (options == null) {
      throw new IllegalArgumentException("options not set");
    }
    if (authCookies == null) {
      throw new IllegalArgumentException("authCookies not set");
    }
    this.hostname = options.getHostname();
    this.port = options.getPort();
    this.userEmail = options.getUserEmail();
    this.authCookies = authCookies.toArray(new Cookie[0]);
    this.remoteApiPath = options.getRemoteApiPath();
    this.maxResponseSize = options.getMaxHttpResponseSize();

    this.appId = appId;
  }

  /**
   * Returns that path to the remote api for this app (if logged in) or null (if not).
   */
  String getRemoteApiPath() {
    return remoteApiPath;
  }

  /**
   * Returns the app id for this app (if logged in) or null (if not).
   */
  String getAppId() {
    return appId;
  }

  String serializeCredentials() {
    StringBuilder out = new StringBuilder();
    out.append("host=" + hostname + "\n");
    out.append("email=" + userEmail + "\n");
    for (Cookie cookie : authCookies) {
      out.append("cookie=" + cookie.getName() + "=" + cookie.getValue() + "\n");
    }
    return out.toString();
  }
  String makeUrl(String path) {
    if (!path.startsWith("/")) {
      throw new IllegalArgumentException("path doesn't start with a slash: " + path);
    }
    String protocol = port == 443 ? "https" : "http";
    return protocol + "://" + hostname + ":" + port + path;
  }

  List<String[]> getHeadersForPost(String mimeType) {
    return Arrays.asList(
        new String[]{"Host", hostname},
        new String[]{"Content-type", mimeType},
        new String[]{"X-appcfg-api-version", "1"});
  }

  List<String[]> getHeadersForGet() {
    return Arrays.asList(
        new String[]{"Host", hostname},
        new String[]{"X-appcfg-api-version", "1"});
  }

  abstract Response get(String path) throws IOException;

  abstract Response post(String path, String mimeType, byte[] body) throws IOException;

  static class Response {
    private final int statusCode;
    private final byte[] responseBody;
    private final String responseCharSet;

    Response(int statusCode, byte[] responseBody, String responseCharSet) {
      this.statusCode = statusCode;
      this.responseBody = responseBody;
      this.responseCharSet = responseCharSet;
    }

    int getStatusCode() {
      return statusCode;
    }

    byte[] getBodyAsBytes() {
      return responseBody;
    }

    String getBodyAsString() {
      return EncodingUtil.getString(responseBody, responseCharSet);
    }
  }
}
