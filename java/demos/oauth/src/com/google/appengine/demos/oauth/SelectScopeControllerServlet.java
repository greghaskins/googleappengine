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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Allows the user to select one or more service scopes to limit the
 * authentication token to.
 * Associated view: selectscope.jsp
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public class SelectScopeControllerServlet extends ControllerServlet {

  @Override
  protected WizardStep getStep() {
    return WizardStep.SELECT_SCOPE;
  }

  @Override
  protected ServletInfo handlePost(HttpServletRequest request,
      HttpServletResponse response, UserInfoContainer userInfo) {

    // Grab the scope values from the request and add them to the user info
    // container.
    String[] selectedScopes = request.getParameterValues("scope");
    if (selectedScopes == null) {
      return ServletInfo.getException(new IllegalArgumentException(
          "Please select a valid scope"));
    }
    ServiceInfoContainer container = new ServiceInfoContainer();
    for (String selectedScope : selectedScopes) {
      for (ServiceInfo scope : ServiceInfo.values()) {
        if (scope.getScopeUri().equals(selectedScope)) {
          container.add(scope);
        }
      }
    }
    userInfo.setServiceInfoContainer(container);

    return ServletInfo.getSuccess();
  }
}
