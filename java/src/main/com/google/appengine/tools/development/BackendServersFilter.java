// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.appengine.api.backends.BackendService;
import com.google.appengine.api.backends.dev.LocalServerController;
import com.google.apphosting.api.ApiProxy;
import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This filter intercepts all request sent to all servers.
 *
 *  There are 5 different request types that this filter will see:
 *
 *  * NORMAL_REQUEST: a normal request sent to the main servlet handler, in this
 * case the filter has no effect.
 *
 *  * REDIRECT_REQUESTED: a request sent to either the main servlet handler or
 * to a load-balancing server.
 *
 *  If the request contains information about which instance to redirect to the
 * filter will verify that the instance is available and either forward the
 * request or respond with a 500 error.
 *
 *  If no instance is specified the filter will pick an idle instance and
 * forward the request, if no idle instances are available a 500 error response
 * is sent.
 *
 *  * DIRECT_SERVER_REQUEST: a request sent directly to the listening port of a
 * specific server instance. The filter will verify that the instance is
 * available and if not respond with a 500 error.
 *
 *  * REDIRECTED_SERVER_REQUEST: a request redirected to a specific instance.
 * The filter will send the request to the handler.
 *
 * * SERVER_STARTUP_REQUEST: startup request sent when servers are started.
 *
 *
 */
public class BackendServersFilter implements Filter {

  static final String BACKEND_REDIRECT_ATTRIBUTE = "com.google.appengine.backend.BackendName";
  static final String INSTANCE_REDIRECT_ATTRIBUTE = "com.google.appengine.backend.BackendInstance";

  static final int SERVER_BUSY_ERROR_CODE = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

  static final int SERVER_STOPPED_ERROR_CODE = HttpServletResponse.SC_NOT_FOUND;

  static final int SERVER_MISSING_ERROR_CODE = HttpServletResponse.SC_BAD_GATEWAY;

  private final BackendServers backendServersManager;

  private Logger logger = Logger.getLogger(BackendServersFilter.class.getName());

  @VisibleForTesting
  BackendServersFilter(BackendServers backendServers) {
    this.backendServersManager = backendServers;
  }

  public BackendServersFilter() {
    this.backendServersManager = BackendServers.getInstance();
  }

  @Override
  public void destroy() {
  }

  /**
   * Main filter method. All request to the dev-appserver pass this method.
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest hrequest = (HttpServletRequest) request;
    HttpServletResponse hresponse = (HttpServletResponse) response;
    RequestType requestType = getRequestType(hrequest);
    logger.finer("got request, type=" + requestType);
    switch (requestType) {
      case NORMAL_REQUEST:
        injectServerApiInfo(null, -1);
        chain.doFilter(hrequest, hresponse);
        break;
      case REDIRECT_REQUESTED:
        doServerRedirect(hrequest, hresponse);
        break;
      case DIRECT_SERVER_REQUEST:
        doDirectServerRequest(hrequest, hresponse, chain);
        break;
      case REDIRECTED_SERVER_REQUEST:
        doRedirectedServerRequest(hrequest, hresponse, chain);
        break;
      case SERVER_STARTUP_REQUEST:
        doStartupRequest(hrequest, hresponse, chain);
        break;
    }
  }

  /**
   * Determine the request type for a given request.
   *
   * @param hrequest The Request to categorize
   * @return The RequestType of the request
   */
  @VisibleForTesting
  RequestType getRequestType(HttpServletRequest hrequest) {
    int serverPort = hrequest.getServerPort();
    String directServerName = backendServersManager.getServerNameFromPort(serverPort);

    if (hrequest.getRequestURI().equals("/_ah/start") && directServerName != null) {
      return RequestType.SERVER_STARTUP_REQUEST;
    } else if (hrequest.getAttribute(BACKEND_REDIRECT_ATTRIBUTE) != null &&
               hrequest.getAttribute(BACKEND_REDIRECT_ATTRIBUTE) instanceof String) {
      return RequestType.REDIRECTED_SERVER_REQUEST;
    } else if (directServerName != null) {
      int directServerReplica = backendServersManager.getServerInstanceFromPort(serverPort);
      if (directServerReplica == -1) {
        return RequestType.REDIRECT_REQUESTED;
      } else {
        return RequestType.DIRECT_SERVER_REQUEST;
      }
    } else {
      String serverRedirectHeader =
          getHeaderOrParameter(hrequest, BackendService.REQUEST_HEADER_BACKEND_REDIRECT);
      if (serverRedirectHeader == null) {
        return RequestType.NORMAL_REQUEST;
      } else {
        return RequestType.REDIRECT_REQUESTED;
      }
    }
  }

  private boolean instanceAcceptsConnections(
      String requestedServer, int instance, HttpServletResponse hresponse) throws IOException {
    if (!backendServersManager.checkInstanceExists(requestedServer, instance)) {
      String msg =
          String.format("Got request to non-configured instance: %d.%s", instance, requestedServer);
      logger.warning(msg);
      hresponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, msg);
      return false;
    }
    if (backendServersManager.checkInstanceStopped(requestedServer, instance)) {
      String msg =
          String.format("Got request to stopped instance: %d.%s", instance, requestedServer);
      logger.warning(msg);
      hresponse.sendError(SERVER_STOPPED_ERROR_CODE, msg);
      return false;
    }

    if (!backendServersManager.acquireServingPermit(requestedServer, instance, true)) {
      String msg = String.format(
          "Got request to server %d.%s but the instance is busy.", instance, requestedServer);
      logger.finer(msg);
      hresponse.sendError(SERVER_BUSY_ERROR_CODE, msg);
      return false;
    }

    return true;
  }

  /**
   * Request that contains either headers or parameters specifying that it
   * should be forwarded either to a specific server and instance, or to a free
   * instance of a specific server.
   */
  private void doServerRedirect(HttpServletRequest hrequest, HttpServletResponse hresponse)
      throws IOException, ServletException {
    String requestedServer =
        backendServersManager.getServerNameFromPort(hrequest.getServerPort());
    if (requestedServer == null) {
      requestedServer =
          getHeaderOrParameter(hrequest, BackendService.REQUEST_HEADER_BACKEND_REDIRECT);
    }

    int instance = getInstanceIdFromRequest(hrequest);
    logger.finest(String.format("redirect request to server: %d.%s", instance, requestedServer));
    if (instance != -1) {
      if (!instanceAcceptsConnections(requestedServer, instance, hresponse)) {
        return;
      }
    } else {
      if (!backendServersManager.checkServerExists(requestedServer)) {
        String msg = String.format("Got request to non-configured server: %s", requestedServer);
        logger.warning(msg);
        hresponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, msg);
        return;
      }
      if (backendServersManager.checkServerStopped(requestedServer)) {
        String msg = String.format("Got request to stopped server: %s", requestedServer);
        logger.warning(msg);
        hresponse.sendError(SERVER_STOPPED_ERROR_CODE, msg);
        return;
      }
      instance = backendServersManager.getAndReserveFreeInstance(requestedServer);
      if (instance == -1) {
        String msg = String.format("all instances of server %s are busy", requestedServer);
        logger.finest(msg);
        hresponse.sendError(SERVER_BUSY_ERROR_CODE, msg);
        return;
      }
    }

    try {
      logger.finer(String.format("forwarding request to server: %d.%s", instance, requestedServer));
      hrequest.setAttribute(BACKEND_REDIRECT_ATTRIBUTE, requestedServer);
      hrequest.setAttribute(INSTANCE_REDIRECT_ATTRIBUTE, Integer.valueOf(instance));
      backendServersManager.forwardToServer(requestedServer, instance, hrequest, hresponse);
    } finally {
      backendServersManager.returnServingPermit(requestedServer, instance);
    }
  }

  /**
   * A request sent straight to the local port of a specific server.
   *
   * If the server is busy with other requests a 500 response is sent.
   *
   */
  private void doDirectServerRequest(
      HttpServletRequest hrequest, HttpServletResponse hresponse, FilterChain chain)
      throws IOException, ServletException {
    int serverPort = hrequest.getServerPort();
    String requestedServer = backendServersManager.getServerNameFromPort(serverPort);
    int instance = backendServersManager.getServerInstanceFromPort(serverPort);
    logger.finest("request to specific server instance: " + instance + "." + requestedServer);

    if (!instanceAcceptsConnections(requestedServer, instance, hresponse)) {
      return;
    }
    try {
      injectServerApiInfo(requestedServer, instance);
      chain.doFilter(hrequest, hresponse);
    } finally {
      backendServersManager.returnServingPermit(requestedServer, instance);
    }
  }

  /**
   * A request forwarded from a different server. The forwarding server is
   * responsible for acquiring the serving permit. All we need to do is to add
   * the ServerApiInfo and forward the request along the chain.
   */
  private void doRedirectedServerRequest(
      HttpServletRequest hrequest, HttpServletResponse hresponse, FilterChain chain)
      throws IOException, ServletException {
    Object backendServerValue = hrequest.getAttribute(BACKEND_REDIRECT_ATTRIBUTE);
    String backendServer = (backendServerValue instanceof String) ?
        ((String) backendServerValue) : null;
    Object instanceValue = hrequest.getAttribute(INSTANCE_REDIRECT_ATTRIBUTE);
    Integer instance = (instanceValue instanceof Integer) ? ((Integer) instanceValue) : null;
    logger.finest("redirected request to server instance: " + instance + "." + backendServer);

    injectServerApiInfo(backendServer, instance);
    chain.doFilter(hrequest, hresponse);
  }

  /**
   * Startup requests do not require any serving permits and can be forwarded
   * along the chain straight away.
   */
  private void doStartupRequest(
      HttpServletRequest hrequest, HttpServletResponse hresponse, FilterChain chain)
      throws IOException, ServletException {
    int serverPort = hrequest.getServerPort();
    String backendServer = backendServersManager.getServerNameFromPort(serverPort);
    int instance = backendServersManager.getServerInstanceFromPort(serverPort);
    logger.finest("startup request to: " + instance + "." + backendServer);
    injectServerApiInfo(backendServer, instance);
    chain.doFilter(hrequest, hresponse);
  }

  @SuppressWarnings("unused")
  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }

  /**
   * Inject information about the current server setup so it is available to the
   * Servers API. This information is stored in the threadLocalAttributes in the
   * current environment.
   *
   * @param currentServer The server that is handling the request
   * @param instance The server instance that is handling the request
   */
  private void injectServerApiInfo(String currentServer, int instance) {
    Map<String, Object> threadLocalAttributes = ApiProxy.getCurrentEnvironment().getAttributes();

    threadLocalAttributes.put(BackendService.INSTANCE_ID_ENV_ATTRIBUTE, instance + "");
    if (currentServer != null) {
      threadLocalAttributes.put(BackendService.BACKEND_ID_ENV_ATTRIBUTE, currentServer);
    }
    Map<String, String> portMapping = backendServersManager.getPortMapping();
    threadLocalAttributes.put(
        BackendService.DEVAPPSERVER_PORTMAPPING_KEY, portMapping);
    if (portMapping.size() > 0) {
      threadLocalAttributes.put(
          LocalServerController.BACKEND_CONTROLLER_ATTRIBUTE_KEY, backendServersManager);
    }
  }

  /**
   * Checks the request headers and request parameters for the specified key
   */
  @VisibleForTesting
  static String getHeaderOrParameter(HttpServletRequest request, String key) {
    String value = request.getHeader(key);
    if (value != null) {
      return value;
    }
    return request.getParameter(key);
  }

  /**
   * Checks request headers and parameters to see if an instance id was
   * specified.
   */
  @VisibleForTesting
  static int getInstanceIdFromRequest(HttpServletRequest request) {
    try {
      return Integer.parseInt(
          getHeaderOrParameter(request, BackendService.REQUEST_HEADER_INSTANCE_REDIRECT));
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  @VisibleForTesting
  static enum RequestType {
    NORMAL_REQUEST, REDIRECT_REQUESTED, DIRECT_SERVER_REQUEST, REDIRECTED_SERVER_REQUEST,
    SERVER_STARTUP_REQUEST;
  }
}
