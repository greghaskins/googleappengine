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

import com.google.gdata.client.Service.GDataRequest;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.util.ServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the request to show a contact image.
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public class ShowImageServlet extends HttpServlet {

  @Override
  protected void doGet(
      HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    // Verify that the user is authorized.
    AuthSubManager authSubManager = new AuthSubManager(request, response);
    if (!authSubManager.isSignedIn()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
          "User is not authorized");
      return;
    }

    // Get the photo link from the query string.
    String photoLink = request.getParameter("link");
    URL photoUrl;
    if (photoLink == null || photoLink.equals("")) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Invalid photo link");
      return;
    }
    try {
      photoUrl = new URL(photoLink);
    } catch (MalformedURLException mue) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
        "Invalid URL: " + photoLink);
      return;
    }

    String mediaType = "";
    InputStream in;
    ContactsService service = new ContactsService(
        this.getServletContext().getInitParameter("application_name"));
    service.setAuthSubToken(authSubManager.getToken(), null);
    try {
      // Request the photo link, with the appropriate auth header.
      GDataRequest gdreq = service.createRequest(GDataRequest.RequestType.QUERY,
          photoUrl, null);
      gdreq.execute();
      mediaType = gdreq.getResponseContentType().getMediaType();
      in  = gdreq.getResponseStream();
    } catch (ServiceException se) {
      // If the contact has no photo, use the default image.  We could also use
      // memcache here.
      in = getServletContext().getResourceAsStream("/static/nopicture.gif");
      mediaType = "image/gif";
    }

    // Set caching headers so the image is not cached.
    response.setHeader("Content-Disposition", "inline");
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("Expires", "0");

    // Write the image bytes to the response.
    response.setContentType(mediaType);
    ServletOutputStream out = response.getOutputStream();
    byte[] buffer = new byte[4096];
    for (int read = 0; (read = in.read(buffer)) != -1;
        out.write(buffer, 0, read));
    out.flush();
    in.close();
  }
}
