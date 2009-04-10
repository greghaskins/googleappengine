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
import com.google.gdata.client.authn.oauth.OAuthSigner;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Allows the user to enter their values for signing a request.
 * Associated view: selectsignatureinfo.jsp
 * @see <a href="http://oauth.net/core/1.0/#signing_process">Section 9 of the
 * OAuth spec</a>
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public class SelectSignatureInfoControllerServlet extends ControllerServlet {

  @Override
  protected WizardStep getStep() {
    return WizardStep.SELECT_SIGNATURE_INFO;
  }

  @Override
  protected ServletInfo handlePost(HttpServletRequest request,
      HttpServletResponse response, UserInfoContainer userInfo) {

    // Grab the variables for the request and set them in the user info
    // container.  If the variables are invalid, display an error to the user.
    String sigmethod = request.getParameter("signaturemethod");
    String consumerKey = request.getParameter("consumerkey");
    String secret = request.getParameter("secret");

    if (sigmethod.equals("default")) {
      sigmethod = SignatureMethod.RSA.getKey();
      consumerKey = "ADD DEFAULT CONSUMER KEY HERE";
      secret = "ADD DEFAULT PRIVATE KEY HERE";
    }

    for (SignatureMethod smethod : SignatureMethod.values()) {
      if (smethod.getKey().equals(sigmethod)) {
        userInfo.setSignatureMethod(smethod);
      }
    }
    if (userInfo.getSignatureMethod() == null) {
      return ServletInfo.getException(new IllegalArgumentException(
          "Invalid Signature Method"));
    }

    if (consumerKey == null || consumerKey.equals("")) {
      return ServletInfo.getException(new IllegalArgumentException(
          "Invalid Consumer Key"));
    }
    if (secret == null || secret.equals("")) {
      return ServletInfo.getException(new IllegalArgumentException(
          "Invalid " + userInfo.getSignatureMethod().getSecretText()));
    }
    if (consumerKey.equals("ADD DEFAULT CONSUMER KEY HERE") ||
        secret.equals("ADD DEFAULT PRIVATE KEY HERE")) {
      return ServletInfo.getException(new IllegalArgumentException(
          "The default values for the consumer key and secret are invalid. " +
          "Please update these values in the code if you'd like to use them. " +
          "Otherwise, choose a different signature method."));
    }

    userInfo.setConsumerKey(consumerKey);
    userInfo.setSecret(secret);

    // Verify that the signing parameters are actually valid.  If they are not
    // valid, an exception will be triggered, which we'll propogate to the user.
    GoogleOAuthParameters oauthParameters = new GoogleOAuthParameters();
    oauthParameters.setOAuthConsumerKey(userInfo.getConsumerKey());
    if (userInfo.getSignatureMethod() == SignatureMethod.HMAC) {
      oauthParameters.setOAuthConsumerSecret(userInfo.getSecret());
    }
    OAuthSigner signer;
    try {
      signer = getOAuthSigner(userInfo, oauthParameters);
    } catch (OAuthException e) {
      return ServletInfo.getException(e);
    } catch (IllegalArgumentException e) {
      return ServletInfo.getException(e);
    }

    return ServletInfo.getSuccess();
  }

}
