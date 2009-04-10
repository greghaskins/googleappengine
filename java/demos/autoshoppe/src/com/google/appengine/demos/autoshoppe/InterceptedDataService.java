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

package com.google.appengine.demos.autoshoppe;

import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Entity;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Iterator;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;

/**
 * InterceptedDataService presents a JSON view of Audit records.
 *
 * @author zoso@google.com (Anirudh Dewani)
 */
public class InterceptedDataService extends HttpServlet {
 
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    UserService service = UserServiceFactory.getUserService();
    ArrayList<Entity> records = null;
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Query query = new Query("Audit");
    query.addSort("logtime", Query.SortDirection.DESCENDING);
    records = new ArrayList<Entity>();
    String emailAddress = null;
    for (Entity audit : ds.prepare(query).asIterable()) {

      //Since its a public sample, mask all email addresses.
      if (audit.hasProperty(AutoShoppeConstants.AUTH_USER)) {
        emailAddress = audit.getProperty(
            AutoShoppeConstants.AUTH_USER).toString();
        audit.removeProperty(AutoShoppeConstants.AUTH_USER);
        audit.setProperty(AutoShoppeConstants.AUTH_USER, "xxxxx@"
            + emailAddress.split("@")[1]);
      }
      records.add(audit);
    }

    ArrayList<Map<String, Object>> jsonRecords =
        new ArrayList<Map<String, Object>>();

    for (Iterator<Entity> iterator = records.iterator(); iterator.hasNext();) {
      Entity entity = (Entity) iterator.next();
      entity.setProperty("Audit",
          entity.getKey().toString());
      jsonRecords.add(entity.getProperties());
    }
    String json = (new Gson()).toJson(jsonRecords);
    response.setContentType("text/plain");
    response.setHeader("Cache-Control", "no-cache");
    ServletOutputStream out = response.getOutputStream();
    out.print(json);
    out.close();
  }
}
