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

package com.google.appengine.demos.contactsapi;

import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the request to sign out of AuthSub.
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public class SignoutServlet extends HttpServlet {
  @Override
  protected void doGet(
      HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    AuthSubManager authSubManager = new AuthSubManager(request, response);
    authSubManager.signout();
    response.sendRedirect("/home.jsp");
  }
}
