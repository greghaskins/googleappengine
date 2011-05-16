// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.users.dev.LoginCookieUtils;
import com.google.apphosting.utils.config.AppEngineWebXml;

import javax.servlet.http.HttpServletRequest;

/**
 * {@code LocalHttpRequestEnvironment} is a simple {@link LocalEnvironment} that
 * retrieves authentication details from the cookie that is maintained by the
 * stub implementation of {@link UserService}.
 *
 */
class LocalHttpRequestEnvironment extends LocalEnvironment {
  /**
   * The name of the HTTP header specifying the default namespace
   * for API calls.
   */
  static final String DEFAULT_NAMESPACE_HEADER = "X-AppEngine-Default-Namespace";
  static final String CURRENT_NAMESPACE_HEADER = "X-AppEngine-Current-Namespace";
  private static final String CURRENT_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".currentNamespace";
  private static final String APPS_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".appsNamespace";

  private static final String USER_ID_KEY =
      "com.google.appengine.api.users.UserService.user_id_key";
  private static final String USER_ORGANIZATION_KEY =
      "com.google.appengine.api.users.UserService.user_organization";
  private static final String X_APPENGINE_QUEUE_NAME = "X-AppEngine-QueueName";

  private final LoginCookieUtils.CookieData loginCookieData;

  public LocalHttpRequestEnvironment(AppEngineWebXml appEngineWebXml, HttpServletRequest request) {
    super(appEngineWebXml);
    this.loginCookieData = LoginCookieUtils.getCookieData(request);
    String requestNamespace = request.getHeader(DEFAULT_NAMESPACE_HEADER);
    if (requestNamespace != null) {
      attributes.put(APPS_NAMESPACE_KEY, requestNamespace);
    }
    String currentNamespace = request.getHeader(CURRENT_NAMESPACE_HEADER);
    if (currentNamespace != null) {
      attributes.put(CURRENT_NAMESPACE_KEY, currentNamespace);
    }
    if (loginCookieData != null) {
      attributes.put(USER_ID_KEY, loginCookieData.getUserId());
      attributes.put(USER_ORGANIZATION_KEY, "");
    }
    if (request.getHeader(X_APPENGINE_QUEUE_NAME) != null) {
      attributes.put(ApiProxyLocalImpl.IS_OFFLINE_REQUEST_KEY, Boolean.TRUE);
    }
  }

  public boolean isLoggedIn() {
    return loginCookieData != null;
  }

  public String getEmail() {
    if (loginCookieData == null) {
      return null;
    } else {
      return loginCookieData.getEmail();
    }
  }

  public boolean isAdmin() {
    if (loginCookieData == null) {
      return false;
    } else {
      return loginCookieData.isAdmin();
    }
  }
}
