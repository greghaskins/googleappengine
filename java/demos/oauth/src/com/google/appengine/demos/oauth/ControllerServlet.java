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

package com.google.appengine.demos.oauth;

import com.google.gdata.client.authn.oauth.GoogleOAuthParameters;
import com.google.gdata.client.authn.oauth.OAuthException;
import com.google.gdata.client.authn.oauth.OAuthHmacSha1Signer;
import com.google.gdata.client.authn.oauth.OAuthRsaSha1Signer;
import com.google.gdata.client.authn.oauth.OAuthSigner;

import java.io.IOException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This class handles the wizard page flow for this example.  If you are
 * learning about the details of OAuth, don't spend too much time studying this
 * code; it is merely the scaffolding for each step of the wizard.  The real
 * details of the OAuth process are in the child *ControllerServlet.java
 * classes.
 *
 * The wizard is separated into multiple steps; each step has a controller
 * servlet (which inherits from this class) and a corresponding jsp file for the
 * view.  A GET request is considered the start of a new step, while a POST
 * request processes the current step before redirecting the user to the next
 * step (or redisplaying the current step, if there was an error).
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public abstract class ControllerServlet extends HttpServlet {

  public static final String ERROR_KEY = "ERROR";
  private static final String USER_INFO_CONTAINER_KEY = "userinfocontainer";
  private static final String HTTP_METHOD_GET_KEY = "GET";
  private static final String HTTP_METHOD_POST_KEY = "POST";

  @Override
  protected void doGet(
      HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    handleRequest(request, response, HTTP_METHOD_GET_KEY);
  }

  @Override
  protected void doPost(
      HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    handleRequest(request, response, HTTP_METHOD_POST_KEY);
  }

  /**
   * Handles an incoming request.  This example supports either GET or POST
   * requests.  If the request is a GET, it assumes the user is beginning this
   * step.  If the request is a POST, it processes the user input and sends the
   * user to the next step.  There are some setup and teardown pieces that both
   * http methods have in common, such as loading/saving user data, setting
   * error messages, and routing to the next page.  All that common
   * functionality is handled by this method.
   *
   * @param request
   * @param response
   * @param requestMethod Either "GET" or "POST".  A "GET" request signals the
   *     start of a new step in the wizard, while a "POST" request indicates
   *     that the user clicked "next".
   * @throws IOException
   * @throws ServletException
   */
  private void handleRequest(HttpServletRequest request,
      HttpServletResponse response, String requestMethod)
      throws IOException, ServletException {

    // Load the user information.
    UserInfoContainer userInfo = getUserInfoContainer(request);

    // Set caching headers so the steps of the wizard aren't cached.
    response.setHeader("Content-Disposition", "inline");
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("Expires", "0");

    // If for some reason the step the user is on doesn't match what is expected
    // (usually when the user hits the back button), start the wizard over.  A
    // more robust implementation could try to correct for this, but we won't
    // worry about it.
    if (getStep() != userInfo.getCurrentStep()) {
      response.sendRedirect("/StartOver");
      return;
    }

    // Process either a GET or a POST request.  In this example, it is assumed
    // that if the request is a POST, the user clicked "next".  (This example
    // could be modified to support a "back" button for the user to move both
    // forwards and backwards through the wizard.)
    ServletInfo servletInfo;
    if (HTTP_METHOD_GET_KEY.equals(requestMethod)) {
      RequestInfo requestInfo = handleGet(request, response, userInfo);
      // ALL GET requests show the view after they're done processing, so we
      // always set the ResponseInfo to ResponseAction.FORWARD.
      servletInfo = new ServletInfo(requestInfo,
          new ResponseInfo(ResponseAction.FORWARD, ""));
    } else if (HTTP_METHOD_POST_KEY.equals(requestMethod)) {
      servletInfo = handlePost(request, response, userInfo);
      // If this is a POST request, and the request is successful, set the user
      // to the next step.
      if (servletInfo.getRequestInfo().getStatus() == RequestStatus.SUCCESS) {
        incrementStep(userInfo);
      }
    } else {
      throw new IOException("Unsupported Request Method: " + requestMethod);
    }

    // If there was an error, set the error message in the request for the view
    // to pick up.
    if (servletInfo.getRequestInfo().getStatus() == RequestStatus.ERROR) {
      setError(request, servletInfo.getRequestInfo().getException());
    }

    // Save the user information.
    setUserInfoContainer(request, userInfo);

    // Send the user to the next step in the wizard (or stay on the current step
    // if there was an error.
    dispatchResponse(request, response, userInfo,
        servletInfo.getResponseInfo());
  }

  /**
   * Allows for each child servlet to handle a "GET" request.
   *
   * @throws IOException
   * @throws ServletException
   */
  protected RequestInfo handleGet(HttpServletRequest request,
      HttpServletResponse response, UserInfoContainer userInfo)
      throws IOException, ServletException {
    return RequestInfo.getSuccess();
  }

  /**
   * Allows for each child servlet to handle a "POST" request.
   *
   * @throws IOException
   */
  protected ServletInfo handlePost(HttpServletRequest request,
      HttpServletResponse response, UserInfoContainer userInfo)
      throws IOException {
    return ServletInfo.getSuccess();
  }

  /** Returns the step associated with the servlet. */
  protected abstract WizardStep getStep();

  /** Associates an error message with the request. */
  protected void setError(HttpServletRequest request, Exception exception) {
    request.setAttribute(ERROR_KEY, exception.getMessage());
    getServletContext().log("ERROR", exception);
    exception.printStackTrace();
  }

  /**
   * Retrieves the user information from the session (or sets it if it doesn't
   * exist).
   */
  private UserInfoContainer getUserInfoContainer(HttpServletRequest request) {
    UserInfoContainer userInfo =
        (UserInfoContainer) request.getSession().getAttribute(
            USER_INFO_CONTAINER_KEY);
    if (userInfo == null) {
      userInfo = new UserInfoContainer();
      setUserInfoContainer(request, userInfo);
    }
    return userInfo;
  }

  /** Sets the user information in the session. */
  private void setUserInfoContainer(HttpServletRequest request,
      UserInfoContainer userInfo) {
    request.getSession().setAttribute(USER_INFO_CONTAINER_KEY, userInfo);
  }

  /** Increments the user from the current step to the next step. */
  private void incrementStep(UserInfoContainer userInfo) {
    WizardStep nextStep = userInfo.getCurrentStep().getNextStep();
    if (nextStep == null) {
      nextStep = WizardStep.WELCOME;
    }
    userInfo.setCurrentStep(nextStep);
  }

  /** Sends the request to the next page. */
  private void dispatchResponse(HttpServletRequest request,
      HttpServletResponse response, UserInfoContainer userInfo,
      ResponseInfo dispatcher) throws ServletException, IOException {
    if (dispatcher.getAction() == ResponseAction.FORWARD) {
      dispatchForward(request, response, userInfo, dispatcher);
    } else if (dispatcher.getAction() == ResponseAction.REDIRECT) {
      dispatchRedirect(response, userInfo, dispatcher);
    }
  }

  /** Redirect to the next step (or to a different location). */
  private void dispatchRedirect(HttpServletResponse response,
      UserInfoContainer userInfo, ResponseInfo dispatcher) throws IOException {
    String location = dispatcher.getLocation();
    if (location.equals("")) {
      location = userInfo.getCurrentStep().getServletName();
    }
    response.sendRedirect(location);
  }

  /** Forward the request/response to the view */
  private void dispatchForward(HttpServletRequest request,
      HttpServletResponse response, UserInfoContainer userInfo,
      ResponseInfo dispatcher) throws ServletException, IOException {
    String location = dispatcher.getLocation();
    if (location.equals("")) {
      location = userInfo.getCurrentStep().getView();
    }
    ServletContext sc = getServletContext();
    RequestDispatcher rd = sc.getRequestDispatcher(location);
    rd.forward(request, response);
  }

  /**
   * Create an {@link OAuthSigner} object based on the user input.
   *
   * @param userInfo User information collected by the wizard.  If the user
   *     selected RSA, this should contain the private key.  If the user
   *     selected HMAC, this should contain the consumer secret.
   * @param oauthParameters
   * @return An {@link OAuthSigner} object.
   * @throws OAuthException If the {@link OAuthSigner} could not be created for
   *     some reason, such as an invalid private key.
   */
  protected OAuthSigner getOAuthSigner(UserInfoContainer userInfo,
      GoogleOAuthParameters oauthParameters) throws OAuthException {
    OAuthSigner signer;
    if (userInfo.getSignatureMethod() == SignatureMethod.RSA) {
      // If the user selected RSA, create a new signer with the private key.
      signer = new OAuthRsaSha1Signer(userInfo.getSecret());
    } else if (userInfo.getSignatureMethod() == SignatureMethod.HMAC) {
      signer = new OAuthHmacSha1Signer();
    } else {
      throw new IllegalArgumentException(
          "Unknown Signature Method: " + userInfo.getSignatureMethod());
    }
    return signer;
  }
}
