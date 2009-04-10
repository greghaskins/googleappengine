/* Copyright (c) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.appengine.demos.contactsapi;

import com.google.gdata.client.http.AuthSubUtil;
import com.google.gdata.util.AuthenticationException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This class manages the AuthSub signin/signout process.
 *
 * @see <a href="http://code.google.com/apis/gdata/authsub.html">Using AuthSub
 * with the Google Data API Client Libraries</a>
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public class AuthSubManager {

  private HttpServletRequest request;
  private HttpServletResponse response;
  private static final String TOKEN_COOKIE_NAME = "TOKEN";
  private static final String SCOPE = "http://www.google.com/m8/feeds/";

  public AuthSubManager(HttpServletRequest req, HttpServletResponse resp) {
    request = req;
    response = resp;
  }

  /**
   * Determines if the user has a valid AuthSub token.
   *
   * @return Whether the user has a valid AuthSub token.
   * @throws IOException
   */
  public Boolean isSignedIn() throws IOException {
    return isSignedIn(false);
  }

  /**
   * Determines if the user has a valid AuthSub token.  The method first checks
   * whether the cookie storing the AuthSub token exists.  If it does, and if
   * the <code>verify</code> parameter is <code>true</code>, the method verifies
   * that the token is valid by asking the server.
   *
   * @param verify Whether to verify that the token is valid by asking the
   *     server.
   * @return Whether the user has a valid AuthSub token.
   * @throws IOException
   */
  public Boolean isSignedIn(Boolean verify) throws IOException {
    // Get the token from the cookie.
    String token = getToken();
    if (token.equals("")) {
      return false;
    }
    if (verify) {
      try {
        // Validate the token against the server.
        Map<String, String> info = AuthSubUtil.getTokenInfo(token, null);
      } catch (GeneralSecurityException gse) {
        return false;
      } catch (AuthenticationException ae) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the url the user should visit in order to begin the AuthSub
   * process.
   */
  public String getSigninUrl() {
    return AuthSubUtil.getRequestUrl(getNextUrl(), SCOPE, false, true);
  }

  /**
   * Calculate and return the full url to redirect to after the user authorizes.
   */
  private String getNextUrl() {
    StringBuilder nextUrl = new StringBuilder();
    nextUrl.append(request.getScheme()).append("://")
        .append(request.getServerName());
    if (request.getServerPort() != 80) {
      nextUrl.append(":").append(request.getServerPort());
    }
    nextUrl.append("/HandleToken");
    return nextUrl.toString();
  }

  /**
   * Processes the AuthSub response from the remote server, requests a
   * long-lived session token, and store this token in a cookie.
   */
  public void storeToken() throws IOException {
    // Get the token from the querystring.
    String token = AuthSubUtil.getTokenFromReply(request.getQueryString());
    if (token == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "No token specified.");
      return;
    }

    String sessionToken;
    try {
      // Exchange the token for a session token.
      sessionToken = AuthSubUtil.exchangeForSessionToken(token, null);
    } catch (GeneralSecurityException gse) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Security error while retrieving session token.");
      return;
    } catch (AuthenticationException ae) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Server rejected one time use token.");
      return;
    }

    // Store the token in a cookie.
    setTokenCookie(sessionToken);
  }

  /**
   * Return the user's AuthSub session token.
   */
  public String getToken() {
    Cookie cookie = getTokenCookie();
    if (cookie != null) {
      return cookie.getValue();
    }
    return "";
  }

  /**
   * Revoke the user's AuthSub session token.
   */
  public void signout() throws IOException {
    try {
      if (isSignedIn()) {
        // Revoke the token.
        AuthSubUtil.revokeToken(getToken(), null);
        // Reset the token cookie.
        setTokenCookie("");
      }
    } catch (GeneralSecurityException gse) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Security error while retrieving session token.");
      return;
    } catch (AuthenticationException ae) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Server rejected one time use token.");
      return;
    }
  }

  /**
   * Get the AuthSub session token from the cookie.
   */
  private Cookie getTokenCookie() {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (TOKEN_COOKIE_NAME.equals(cookie.getName())) {
        return cookie;
      }
    }
    return null;
  }

  /**
   * Set the AuthSub session token in a cookie.
   */
  private void setTokenCookie(String token) {
    Cookie cookie = new Cookie(TOKEN_COOKIE_NAME, token);
    response.addCookie(cookie);
  }
}
