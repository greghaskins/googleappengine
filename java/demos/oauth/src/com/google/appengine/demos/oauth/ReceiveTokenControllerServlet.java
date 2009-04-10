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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The user is redirected to this servlet after they have successfully
 * authenticated with Google.
 * Associated view: receivetoken.jsp
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public class ReceiveTokenControllerServlet extends ControllerServlet {

  @Override
  protected WizardStep getStep() {
    return WizardStep.RECEIVE_TOKEN;
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

    String accessToken = "";
    try {
      // Exchange the authorized request token for the access token.  Both
      // the authorized request token and token secret are in the querystring
      // of the url.  This method parses them, and uses them to retrieve the
      // access token from the service provider.  If the user denied
      // authorization of the request token, this step would fail.
      accessToken = oauthHelper.getAccessToken(request.getQueryString(),
          oauthParameters);

      // Save both the access token and token secret.  Unlike the request token
      // secret, we actually need to save the access token secret, since it will
      // be used for all subsequent requests to the service provider.
      userInfo.setAccessToken(accessToken);
      userInfo.setTokenSecret(oauthParameters.getOAuthTokenSecret());
    } catch (OAuthException oex) {
      return RequestInfo.getException(oex);
    }
    return RequestInfo.getSuccess();
  }
}
