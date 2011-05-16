// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.tools.remoteapi;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles logging into App Engine using
 * <a href="http://code.google.com/apis/accounts/docs/AuthForInstalledApps.html"
 * >ClientLogin</a>.
 *
 */
class ClientLogin {
  private static final int MAX_RESPONSE_SIZE = 1024 * 1024;

  private ClientLogin() {}

  /**
   * Authenticates the user using ClientLogin. This requires two HTTP requests,
   * to get a token from Google and to exchange it for cookies from App Engine.
   */
  static List<Cookie> login(String host, String email, String password)
      throws IOException {
    String token = getClientLoginToken(email, password);
    return getAppEngineLoginCookies(host, token);
  }

  /**
   * Gets an authentication token from Gaia, given the user's email address and password.
   */
  private static String getClientLoginToken(String email, String password) throws IOException {
    if (email == null || email.isEmpty()) {
      throw new IllegalArgumentException("email not set");
    }
    if (password == null || password.isEmpty()) {
      throw new IllegalArgumentException("password not set");
    }

    PostMethod post = new PostMethod("https://www.google.com/accounts/ClientLogin");
    post.addParameter("Email", email);
    post.addParameter("Passwd", password);
    post.addParameter("service", "ah");

    post.addParameter("source", "Google-remote_api-java-1.0");

    post.addParameter("accountType", "HOSTED_OR_GOOGLE");

    HttpClient client = new HttpClient();
    client.executeMethod(post);

    if (post.getStatusCode() == 200) {
      return readClientLoginResponse(post).get("Auth");
    } else if (post.getStatusCode() == 403) {
      Map<String, String> response = readClientLoginResponse(post);
      String reason = response.get("Error");
      if ("BadAuthentication".equals(reason)) {
        String info = response.get("Info");
        if (!info.isEmpty()) {
          reason = reason + " " + info;
        }
      }
      throw new LoginException("Login failed. Reason: " + reason);
    } else if (post.getStatusCode() == 401) {
      throw new LoginException("Email \"" + email + "\" and password do not match.");
    } else {
      throw new LoginException("Bad authentication response: " + post.getStatusCode());
    }
  }

  private static Map<String, String> readClientLoginResponse(PostMethod post) throws IOException {
    String body = post.getResponseBodyAsString(MAX_RESPONSE_SIZE);
    return parseClientLoginResponse(body);
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
   * Logs into App Engine and returns the cookies needed to make authenticated requests.
   */
  private static List<Cookie> getAppEngineLoginCookies(String host, String clientLoginToken)
      throws IOException {

    String url = "https://" + host + "/_ah/login"
        + "?auth=" + URLEncoder.encode(clientLoginToken, "UTF-8")
        + "&continue=http://localhost/";

    GetMethod get = new GetMethod(url);
    get.setFollowRedirects(false);

    HttpClient client = new HttpClient();
    client.executeMethod(get);

    if (get.getStatusCode() == 302) {
      return new ArrayList<Cookie>(Arrays.asList(client.getState().getCookies()));
    } else {
      throw new LoginException("unexpected response from app engine: " + get.getStatusCode());
    }
  }

}
