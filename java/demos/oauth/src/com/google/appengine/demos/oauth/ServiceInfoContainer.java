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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Container that stores multiple {@link ServiceInfo} objects.
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public class ServiceInfoContainer implements Serializable {

  List<ServiceInfo> scopes;

  public ServiceInfoContainer() {
    scopes = new ArrayList<ServiceInfo>();
  }

  public void add(ServiceInfo scope) {
    scopes.add(scope);
  }

  public List<ServiceInfo> get() {
    return Collections.unmodifiableList(scopes);
  }

  /**
   * Return the scope for all servers, which is each service's scope,
   * concatenated with a whitespace.
   */
  public String getScopeString() {
    StringBuilder builder = new StringBuilder();
    for (ServiceInfo scope : scopes) {
      if (builder.length() > 0) {
        builder.append(" ");
      }
      builder.append(scope.getScopeUri());
    }
    return builder.toString();
  }
}
