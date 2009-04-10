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

import java.io.IOException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This servlet resets the user information and redirects them to the first step
 * of the wizard.
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public class StartOverServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    request.getSession().setAttribute("userinfocontainer", null);
    response.setHeader("Content-Disposition", "inline");
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("Expires", "0");
    Cookie[] cookies = request.getCookies();
    for (Cookie cookie : cookies) {
      cookie.setValue(null);
      cookie.setMaxAge(0);
      response.addCookie(cookie);
    }
    response.sendRedirect(WizardStep.WELCOME.getServletName());
  }
}
