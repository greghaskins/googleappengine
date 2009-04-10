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

/**
 * Information related to each supported signature method.
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public enum SignatureMethod {
  HMAC("hmac", "HMAC-SHA1", "Consumer Secret"),
  RSA("rsa", "RSA-SHA1", "Private Key");

  private String key;
  private String display;
  private String secretText;

  SignatureMethod(String key, String display, String secretText) {
    this.key = key;
    this.display = display;
    this.secretText = secretText;
  }

  public String getKey() {
    return key;
  }

  public String getSecretText() {
    return secretText;
  }

  @Override
  public String toString() {
    return display;
  }
}
