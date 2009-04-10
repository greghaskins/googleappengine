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

import com.google.gdata.client.authn.oauth.GoogleOAuthHelper;
import com.google.gdata.client.authn.oauth.GoogleOAuthParameters;
import com.google.gdata.client.authn.oauth.OAuthException;
import com.google.gdata.client.authn.oauth.OAuthSigner;
import java.io.UnsupportedEncodingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Displays an information page before redirecting the user to Google to
 * authenticate.
 * Associated view: redirecttogoogle.jsp
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public class RedirectToGoogleControllerServlet extends ControllerServlet {

  public static final String USER_AUTHORIZATION_URL_KEY =
      "userauthorizationurl";

  @Override
  protected WizardStep getStep() {
    return WizardStep.REDIRECT_TO_GOOGLE;
  }

  @Override
  protected RequestInfo handleGet(HttpServletRequest request,
      HttpServletResponse response, UserInfoContainer userInfo) {

    // Set the OAuth parameters.  At this point in the process, we only know the
    // consumer key, and the consumer secret (if the user is signing with HMAC).
    // The other related parameters will be retrieved later one.
    GoogleOAuthParameters oauthParameters = new GoogleOAuthParameters();
    oauthParameters.setOAuthConsumerKey(userInfo.getConsumerKey());
    if (userInfo.getSignatureMethod() == SignatureMethod.HMAC) {
      oauthParameters.setOAuthConsumerSecret(userInfo.getSecret());
    }

    // Create the OAuth signer based on the signature method the user selected.
    OAuthSigner signer;
    try {
      signer = getOAuthSigner(userInfo, oauthParameters);
    } catch (OAuthException e) {
      return RequestInfo.getException(e);
    } catch (IllegalArgumentException e) {
      return RequestInfo.getException(e);
    }

    // Create the {@link GoogleOAuthHelper} object.  This object is responsible
    // for all communication with service provider (i.e. Google) during the
    // OAuth process.
    GoogleOAuthHelper oauthHelper = new GoogleOAuthHelper(signer);

    // Set the scope associated with the service.  The scope is only needed when
    // requesting the OAuth token.
    oauthParameters.setScope(
        userInfo.getServiceInfoContainer().getScopeString());

    String requestUrl = "";
    try {
      // Retrieve the unauthorized request token and token secret.
      oauthHelper.getUnauthorizedRequestToken(oauthParameters);

      // Store the retrieved parameters locally.  There will always be a request
      // token, but there may or may not be a token secret, depending on the
      // signature method and the service provider's implementation.
      userInfo.setRequestToken(oauthParameters.getOAuthToken());
      if (userInfo.getSignatureMethod() == SignatureMethod.HMAC) {
        userInfo.setTokenSecret(oauthParameters.getOAuthTokenSecret());
      }

      // Create the callback url and set it as a parameter.  The callback url
      // is the url the user should be redirected to after authorizing with
      // the service provider.  See the {@link #getCallbackUrl} method for more
      // details on how the callback url was generated.
      oauthParameters.setOAuthCallback(getCallbackUrl(request, userInfo));

      // Create the final url to send the user to in order to authorize with the
      // service provider.  This url will contain the oauth token as well as the
      // callback url.
      requestUrl = oauthHelper.createUserAuthorizationUrl(oauthParameters);
    } catch (OAuthException oex) {
      return RequestInfo.getException(oex);
    } catch (UnsupportedEncodingException e) {
      return RequestInfo.getException(e);
    }

    // Set the user authorization url as a request attribute, since we want to
    // display it to the user before redirecting.  If this weren't an example,
    // you could just redirect the user to the authorization url at this point.
    request.setAttribute(USER_AUTHORIZATION_URL_KEY, requestUrl);


    return RequestInfo.getSuccess();
  }

  @Override
  protected ServletInfo handlePost(HttpServletRequest request,
      HttpServletResponse response, UserInfoContainer userInfo) {
    // Redirect the user to the service provider in order to authenticate and
    // authorize.
    return ServletInfo.getRedirect(request.getParameter(
        USER_AUTHORIZATION_URL_KEY));
  }

  /**
   * Return the callback url for the user to be redirected to after
   * authenticating.
   * @throws UnsupportedEncodingException
   */
  private String getCallbackUrl(HttpServletRequest request,
      UserInfoContainer userInfo) throws UnsupportedEncodingException {
    // Figure out the current domain/port/path and use that as the base url to
    // redirect to.  The servlet to redirect to is determined from the next step
    // in the wizard, which is {@link WizardStep.RECEIVE_TOKEN}.
    StringBuilder callbackUrl = new StringBuilder();
    callbackUrl.append(request.getScheme()).append("://")
        .append(request.getServerName());
    if (request.getServerPort() != 80) {
      callbackUrl.append(":").append(request.getServerPort());
    }
    callbackUrl.append(
        userInfo.getCurrentStep().getNextStep().getServletName());

    // If we are signing using HMAC, we also need to remember the token secret
    // associated with the request token.  There are a couple ways to do this.
    // One, we can save this value on the server side, either in a session or
    // the datastore (keyed off the request token).  However that would require
    // more maintenance on our end, since the token secrets would have to be
    // purged every now and then.  The other options is to store the token
    // secret as a query parameter in the callback url.  This has the advantage
    // of making the OAuth process completely stateless.  The code below does
    // just that.
    if (userInfo.getSignatureMethod() == SignatureMethod.HMAC &&
        userInfo.getTokenSecret() != null) {
      callbackUrl.append("?oauth_token_secret=").append(
          java.net.URLEncoder.encode(userInfo.getTokenSecret(), "UTF-8"));
    }
    return callbackUrl.toString();
  }


}
