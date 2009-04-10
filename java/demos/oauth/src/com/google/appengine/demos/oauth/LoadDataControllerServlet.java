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

import com.google.gdata.client.GoogleService;
import com.google.gdata.client.Service.GDataRequest;
import com.google.gdata.client.authn.oauth.GoogleOAuthParameters;
import com.google.gdata.client.authn.oauth.OAuthException;
import com.google.gdata.client.authn.oauth.OAuthSigner;
import com.google.gdata.util.ServiceException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Once the user has authenticated using OAuth, this step uses the OAuth token
 * to load data from a Google service.
 * Associated view: loaddata.jsp
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public class LoadDataControllerServlet extends ControllerServlet {

  @Override
  protected WizardStep getStep() {
    return WizardStep.LOAD_DATA;
  }

  @Override
  protected RequestInfo handleGet(HttpServletRequest request,
      HttpServletResponse response, UserInfoContainer userInfo) {

    // Set the OAuth parameters.  At this point, we have collected all the
    // parameters we need.  That includes the consumer key and the access token,
    // and the consumer secret and token secret (if the user is using HMAC).
    GoogleOAuthParameters oauthParameters = new GoogleOAuthParameters();
    oauthParameters.setOAuthConsumerKey(userInfo.getConsumerKey());
    oauthParameters.setOAuthToken(userInfo.getAccessToken());
    if (userInfo.getSignatureMethod() == SignatureMethod.HMAC) {
      oauthParameters.setOAuthConsumerSecret(userInfo.getSecret());
      oauthParameters.setOAuthTokenSecret(userInfo.getTokenSecret());
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

    // Load the information for the service the user selected.
    ServiceInfo serviceInfo = userInfo.getServiceInfoContainer().get().get(0);
    URL feedUrl;
    try {
      // Convert the feed url string to a {@link URL} object.
      String feedUrlString = serviceInfo.getFeedUri();
      feedUrlString += feedUrlString.indexOf("?") > 0 ? "&" : "?";
      feedUrlString += "prettyprint=true";
      feedUrl = new URL(feedUrlString);
    } catch (MalformedURLException muex) {
      return RequestInfo.getException(muex);
    }
    // Create a service object
    GoogleService googleService =
        new GoogleService(serviceInfo.getServiceKey(), "oauth-sample-app");
    try {
      // Set the oauth credentials on the service
      googleService.setOAuthCredentials(oauthParameters, signer);

      // Load the feed.
      GDataRequest gdRequest = googleService.createFeedRequest(feedUrl);
      gdRequest.execute();

      // Stream the xml feed to the output.
      InputStream is  = gdRequest.getResponseStream();
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(is, "UTF-8"));
      StringBuilder sb = new StringBuilder();
      String line = null;
      try {
          while ((line = reader.readLine()) != null) {
              sb.append(line + "\n");
          }
      } catch (IOException e) {
        return RequestInfo.getException(e);
      } finally {
        try {
          is.close();
        } catch (IOException e) {
          return RequestInfo.getException(e);
        }
      }
      request.setAttribute("feed_raw_xml", sb.toString());
    } catch (IOException ioe) {
      return RequestInfo.getException(ioe);
    } catch (ServiceException se) {
      return RequestInfo.getException(se);
    } catch (OAuthException oe) {
      return RequestInfo.getException(oe);
    }
    return RequestInfo.getSuccess();
  }
}
