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
 * Stores values related to the response, such as which step to go to next.
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public final class ResponseInfo {

  private ResponseAction action;
  // The next servlet of page to either forward or redirect to.  If this is
  // blank, the location is determined from the current step.
  private String location;

  public ResponseInfo(ResponseAction action, String location) {
    this.action = action;
    this.location = location;
  }

  public ResponseAction getAction() {
    return action;
  }

  public String getLocation() {
    return location;
  }
}
