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
 * Stores values related to the request, such as whether the request was
 * successful or had errors. If there were errors, store an associated error
 * message also.
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public final class RequestInfo {

  private RequestStatus status;
  private Exception exception;

  /** Private constructor.  Use the static constructors below. */
  private RequestInfo(RequestStatus status, Exception exception) {
    this.status = status;
    this.exception = exception;
  }

  /** Creates a successful request. */
  public static RequestInfo getSuccess() {
    return new RequestInfo(RequestStatus.SUCCESS, null);
  }

  /** Creates a request with errors, based off an error message. */
  public static RequestInfo getException(Exception exception) {
    return new RequestInfo(RequestStatus.ERROR, exception);
  }

  public RequestStatus getStatus() {
    return status;
  }

  public Exception getException() {
    return exception;
  }


}
