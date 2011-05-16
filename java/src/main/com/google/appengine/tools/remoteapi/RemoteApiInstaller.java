// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.tools.remoteapi;

import com.google.appengine.api.users.dev.LoginCookieUtils;
import com.google.apphosting.api.ApiProxy;

import org.apache.commons.httpclient.Cookie;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Installs and uninstalls the remote API. While the RemoteApi is installed,
 * all App Engine calls will be sent to a remote server.
 *
 * <p>This class is intended to be used on a single thread.</p>
 *
 */
public class RemoteApiInstaller {
  private static final Pattern PAIR_REGEXP = Pattern.compile("([a-z0-9_-]+): +([~a-z0-9_-]+)");

  private static final ConsoleHandler REMOTE_METHOD_HANDLER = new ConsoleHandler();
  static {
    REMOTE_METHOD_HANDLER.setFormatter(new Formatter() {
      @Override
      public String format(LogRecord record) {
        return record.getMessage() + "\n";
      }
    }) ;
    REMOTE_METHOD_HANDLER.setLevel(Level.FINE);
  }

  private AppEngineClient installedClient;

  private RemoteApiDelegate installedDelegate;
  private ApiProxy.Delegate savedDelegate;

  private ApiProxy.Environment installedEnv;
  private ApiProxy.Environment savedEnv;

  private boolean needUninstall;

  /**
   * Installs the remote API using the provided options.  Logs into the remote
   * application using the credentials available via these options.
   *
   * <p>Warning: not thread-safe. This method may be used only on a single
   * thread, and all App Engine API calls must be made on the same thread.
   * (This restriction may be lifted in a future release.)</p>
   *
   * @throws IllegalArgumentException if the server or credentials weren't provided.
   * @throws IllegalStateException if already installed
   * @throws LoginException if unable to log in.
   * @throws IOException if unable to connect to the remote API.
   */
  public void install(RemoteApiOptions options) throws IOException {
    options = options.copy();
    if (options.getHostname() == null) {
      throw new IllegalArgumentException("server not set in options");
    }
    if (options.getUserEmail() == null) {
      throw new IllegalArgumentException("credentials not set in options");
    }
    if (needUninstall) {
      throw new IllegalStateException("remote API is already installed");
    }
    savedDelegate = ApiProxy.getDelegate();
    savedEnv = ApiProxy.getCurrentEnvironment();
    needUninstall = true;

    installedClient = login(options);
    installedDelegate = createDelegate(options, installedClient, savedDelegate);
    installedEnv = createEnv(options, installedClient);

    ApiProxy.setDelegate(installedDelegate);
    ApiProxy.setEnvironmentForCurrentThread(installedEnv);
  }

  /**
   * Uninstalls the remote API. If any async calls are in progress, waits for
   * them to finish.
   *
   * <p>If the remote API isn't installed, this method has no effect.</p>
   */
  public void uninstall() {
    if (needUninstall) {
      if (installedDelegate != ApiProxy.getDelegate()) {
        throw new IllegalStateException(
            "Can't uninstall because the current delegate has been modified.");
      }
      if (installedEnv != ApiProxy.getCurrentEnvironment()) {
        throw new IllegalStateException(
          "Can't uninstall because the current environment has been modified.");
      }
      ApiProxy.setDelegate(savedDelegate);
      ApiProxy.setEnvironmentForCurrentThread(savedEnv);
      needUninstall = false;
      savedDelegate = null;
      savedEnv = null;

      installedDelegate.shutdown();

      installedDelegate = null;
      installedEnv = null;
      installedClient = null;
    }
  }

  /**
   * Returns a string containing the cookies associated with this
   * connection. The string can be used to create a new connection
   * without logging in again by using {@link RemoteApiOptions#reuseCredentials}.
   * By storing credentials to a file, we can avoid repeated password
   * prompts in command-line tools. (Note that the cookies will expire
   * based on the setting under Application Settings in the admin console.)
   *
   * <p>Beware: it's important to keep this string private, as it
   * allows admin access to the app as the current user.</p>
   */
  public String serializeCredentials() {
    return installedClient.serializeCredentials();
  }

  /**
   * Starts logging remote API method calls to the console. (Useful within tests.)
   */
  public void logMethodCalls() {
    Logger logger = Logger.getLogger(RemoteApiDelegate.class.getName());
    logger.setLevel(Level.FINE);
    if (!Arrays.asList(logger.getHandlers()).contains(REMOTE_METHOD_HANDLER)) {
      logger.addHandler(REMOTE_METHOD_HANDLER);
    }
  }

  public void resetRpcCount() {
    installedDelegate.resetRpcCount();
  }

  /**
   * Returns the number of RPC calls made since the API was installed
   * or {@link #resetRpcCount} was called.
   */
  public int getRpcCount() {
    return installedDelegate.getRpcCount();
  }

  protected AppEngineClient login(RemoteApiOptions options) throws IOException {
    return loginImpl(options);
  }

  protected RemoteApiDelegate createDelegate(RemoteApiOptions options, AppEngineClient client, ApiProxy.Delegate originalDelegate) {
    return new RemoteApiDelegate(new RemoteRpc(client), options, originalDelegate);
  }

  protected ApiProxy.Environment createEnv(RemoteApiOptions options, AppEngineClient client) {
    return new ToolEnvironment(client.getAppId(), options.getUserEmail());
  }

  /**
   * Submits credentials and gets cookies for logging in to App Engine.
   * (Also downloads the appId from the remote API.)
   * @return an AppEngineClient containing credentials (if successful)
   * @throws LoginException for a login failure
   * @throws IOException for other connection failures
   */
  private AppEngineClient loginImpl(RemoteApiOptions options) throws IOException {
    List<Cookie> authCookies;
    if (options.getCredentialsToReuse() != null) {
      authCookies = parseSerializedCredentials(options.getUserEmail(), options.getHostname(),
          options.getCredentialsToReuse());
    } else if (options.getHostname().equals("localhost")) {
      authCookies = Collections.singletonList(
          makeDevAppServerCookie(options.getHostname(), options.getUserEmail()));
    } else {
      authCookies =
          ClientLogin.login(options.getHostname(), options.getUserEmail(), options.getPassword());
    }

    String appId = getAppIdFromServer(authCookies, options);
    return AppEngineClient.newInstance(options, authCookies, appId);
  }
  public static Cookie makeDevAppServerCookie(String hostname, String email) {
    String cookieValue = email + ":true:" + LoginCookieUtils.encodeEmailAsUserId(email);
    Cookie cookie = new Cookie(hostname, LoginCookieUtils.COOKIE_NAME, cookieValue);
    cookie.setPath("/");
    return cookie;
  }

  private String getAppIdFromServer(List<Cookie> authCookies, RemoteApiOptions options)
      throws IOException {
    AppEngineClient tempClient = AppEngineClient.newInstance(options, authCookies, null);
    AppEngineClient.Response response = tempClient.get(options.getRemoteApiPath());
    int status = response.getStatusCode();
    if (status != 200) {
      throw new IOException("can't get appId from remote api; status code = " + status);
    }
    String body = response.getBodyAsString();
    Map<String, String> props = parseYamlMap(body);
    String appId = props.get("app_id");
    if (appId == null) {
      throw new IOException("unexpected response from remote api: " + body);
    }
    return appId;
  }

  /**
   * Parses the response from the remote API as a YAML map.
   */
  static Map<String, String> parseYamlMap(String input) {

    Map<String, String> result = new HashMap<String, String>();
    input = input.trim();
    if (!input.startsWith("{") || !input.endsWith("}")) {
      return Collections.emptyMap();
    }
    input = input.substring(1, input.length() - 1);

    String[] pairs = input.split(", +");
    for (String pair : pairs) {
      Matcher matcher = PAIR_REGEXP.matcher(pair);
      if (matcher.matches()) {
        result.put(matcher.group(1), matcher.group(2));
      }
    }
    return result;
  }

  static List<Cookie> parseSerializedCredentials(String expectedEmail, String expectedHost,
      String serializedCredentials) throws IOException {

    Map<String, List<String>> props = parseProperties(serializedCredentials);
    checkOneProperty(props, "email");
    checkOneProperty(props, "host");
    String email = props.get("email").get(0);
    if (!expectedEmail.equals(email)) {
      throw new IOException("credentials don't match current user email");
    }
    String host = props.get("host").get(0);
    if (!expectedHost.equals(host)) {
      throw new IOException("credentials don't match current host");
    }

    List<Cookie> result = new ArrayList<Cookie>();
    for (String line : props.get("cookie")) {
      result.add(parseCookie(line, host));
    }
    return result;
  }

  private static Cookie parseCookie(String line, String host) throws IOException {
    int firstEqual = line.indexOf('=');
    if (firstEqual < 1) {
      throw new IOException("invalid cookie in credentials");
    }
    String key = line.substring(0, firstEqual);
    String value = line.substring(firstEqual + 1);
    Cookie cookie = new Cookie(host, key, value);
    cookie.setPath("/");
    return cookie;
  }

  private static void checkOneProperty(Map<String, List<String>> props, String key)
      throws IOException {
    if (props.get(key).size() != 1) {
      String message = "invalid credential file (should have one property named '" + key + "')";
      throw new IOException(message);
    }
  }

  private static Map<String, List<String>> parseProperties(String serializedCredentials) {
    Map<String, List<String>> props = new HashMap<String, List<String>>();
    for (String line : serializedCredentials.split("\n")) {
      line = line.trim();
      if (!line.startsWith("#") && line.contains("=")) {
        int firstEqual = line.indexOf('=');
        String key = line.substring(0, firstEqual);
        String value = line.substring(firstEqual + 1);
        List<String> values = props.get(key);
        if (values == null) {
          values = new ArrayList<String>();
          props.put(key, values);
        }
        values.add(value);
      }
    }
    return props;
  }
}
