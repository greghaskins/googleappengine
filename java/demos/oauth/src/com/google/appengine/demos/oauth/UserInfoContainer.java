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

import java.io.Serializable;

/**
 * Container class to hold all the data thats collected from each step of the
 * wizard.  This class is persisted to the http session after each request, but
 * it could just as well be stored in the data store or in memecached.
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public final class UserInfoContainer implements Serializable {

  SignatureMethod signatureMethod;
  ServiceInfoContainer serviceInfoContainer;
  WizardStep step = WizardStep.WELCOME;
  String consumerKey;
  String secret;
  String tokenSecret;
  String accessToken;
  String requestToken;

  public void setRequestToken(String value) {
    requestToken = value;
  }

  public String getRequestToken() {
    return requestToken;
  }

  public void setAccessToken(String value) {
    accessToken = value;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public void setConsumerKey(String value) {
    consumerKey = value;
  }

  public String getConsumerKey() {
    return consumerKey;
  }

  public void setSecret(String value) {
    secret = value;
  }

  public String getSecret() {
    return secret;
  }

  public void setTokenSecret(String value) {
    tokenSecret = value;
  }

  public String getTokenSecret() {
    return tokenSecret;
  }

  public void setServiceInfoContainer(ServiceInfoContainer value) {
    serviceInfoContainer = value;
  }

  public ServiceInfoContainer getServiceInfoContainer() {
    return serviceInfoContainer;
  }

  public void setCurrentStep(WizardStep value) {
    step = value;
  }

  public WizardStep getCurrentStep() {
    return step;
  }

  public void setSignatureMethod(SignatureMethod value) {
    signatureMethod = value;
  }

  public SignatureMethod getSignatureMethod() {
    return signatureMethod;
  }
}
