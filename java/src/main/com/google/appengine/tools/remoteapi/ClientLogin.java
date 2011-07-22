// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.tools.remoteapi;

import org.apache.commons.httpclient.Cookie;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles logging into App Engine using
 * <a href="http://code.google.com/apis/accounts/docs/AuthForInstalledApps.html"
 * >ClientLogin</a>.  This class does not rely on any particular mechanism for
 * sending HTTP requests to ClientLogin.  It instead exposes template methods
 * for subclasses to implement using different mechanisms.
 *
 */
abstract class ClientLogin {

  /**
   * Authenticates the user using ClientLogin. This requires two HTTP requests,
   * one to get a token from Google and one to exchange it for cookies from App Engine.
   */
  public List<Cookie> login(String host, String email, String password) throws IOException {
    if (email == null || email.isEmpty()) {
      throw new IllegalArgumentException("email not set");
    }
    if (password == null || password.isEmpty()) {
      throw new IllegalArgumentException("password not set");
    }
    List<String[]> postParams = getClientLoginPostParams(email, password);
    PostResponse authResponse = executePost(
        "https://www.google.com/accounts/ClientLogin", postParams);
    String token = processAuthResponse(authResponse, email);
    String url = "https://" + host + "/_ah/login"
        + "?auth=" + URLEncoder.encode(token, "UTF-8")
        + "&continue=http://localhost/";
    return getAppEngineLoginCookies(url);
  }

  /**
   * Provides the parameter names and values that need to be posted to the
   * ClientLogin url.  Each element of the returned {@link List} is an array of
   * size 2 where the String at index 0 is the parameter name and the String at
   * index 1 is the parameter value.
   */
  private static List<String[]> getClientLoginPostParams(String email, String password) {
    return Arrays.asList(
        new String[] {"Email", email},
        new String[] {"Passwd", password},
        new String[] {"service", "ah"},
        new String[] {"source", "Google-remote_api-java-1.0"},
        new String[] {"accountType", "HOSTED_OR_GOOGLE"});
  }

  private String processAuthResponse(PostResponse authResponse, String email)
      throws LoginException {
    if (authResponse.statusCode == 200 || authResponse.statusCode == 403) {
      Map<String, String> responseMap = parseClientLoginResponse(authResponse.body);
      if (authResponse.statusCode == 200) {
        return responseMap.get("Auth");
      } else {
        String reason = responseMap.get("Error");
        if ("BadAuthentication".equals(reason)) {
          String info = responseMap.get("Info");
          if (info != null && !info.isEmpty()) {
            reason = reason + " " + info;
          }
        }
        throw new LoginException("Login failed. Reason: " + reason);
      }
    } else if (authResponse.statusCode == 401) {
      throw new LoginException("Email \"" + email + "\" and password do not match.");
    } else {
      throw new LoginException("Bad authentication response: " + authResponse.statusCode);
    }
  }

  /**
   * Parses the key-value pairs in the ClientLogin response.
   */
  private static Map<String, String> parseClientLoginResponse(String body) {
    Map<String, String> response = new HashMap<String, String>();
    for (String line : body.split("\n")) {
      int eqIndex = line.indexOf("=");
      if (eqIndex > 0) {
        response.put(line.substring(0, eqIndex), line.substring(eqIndex + 1));
      }
    }
    return response;
  }

  /**
   * Executes an HTTP Post to the provided url with the provided params.
   */
  abstract PostResponse executePost(String url, List<String[]> postParams) throws IOException;

  /**
   * Logs into App Engine and returns the cookies needed to make authenticated requests.
   */
  abstract List<Cookie> getAppEngineLoginCookies(String url) throws IOException;

  /**
   * Represents a post response.
   */
  static class PostResponse {
    final int statusCode;
    final String body;
    PostResponse(int statusCode, String body) {
      this.statusCode = statusCode;
      this.body = body;
    }
  }
}
