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
 * Container class to store information about both the status of the request,
 * and where to send the response.
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public final class ServletInfo {

  private RequestInfo requestInfo;
  private ResponseInfo responseInfo;

  public ServletInfo(RequestInfo requestInfo, ResponseInfo responseInfo) {
    this.requestInfo = requestInfo;
    this.responseInfo = responseInfo;
  }

  public static ServletInfo getSuccess() {
    return new ServletInfo(RequestInfo.getSuccess(),
        new ResponseInfo(ResponseAction.REDIRECT, ""));
  }

  public static ServletInfo getRedirect(String redirect) {
    return new ServletInfo(RequestInfo.getSuccess(),
        new ResponseInfo(ResponseAction.REDIRECT, redirect));
  }

  public static ServletInfo getException(Exception ex) {
    return new ServletInfo(RequestInfo.getException(ex),
        new ResponseInfo(ResponseAction.FORWARD, ""));
  }

  public RequestInfo getRequestInfo() {
    return requestInfo;
  }

  public ResponseInfo getResponseInfo() {
    return responseInfo;
  }
}
