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

package com.google.appengine.demos.memoreez;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;

import java.io.IOException;
import java.util.List;

import javax.jdo.PersistenceManager;
import javax.servlet.http.*;

/**
 * Servlet for retrieving and returning all data needed by the client during
 * the course of the game, including the total number of cells in the
 * datastore and the color of a cell at a given position.
 * 
 * @author jasonacooper
 */
public class MemoreezServlet extends HttpServlet {
  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {

    resp.setContentType("text/plain");

    // Instantiate a new PersistenceManager which provides an interface for
    // interacting with App Engine's datastore, enabling queries as well as
    // addition and removal.
    PersistenceManager pm = PMF.get().getPersistenceManager();

    String cell = req.getParameter("cell");

    if (cell == null) {
      // If no cell position is provided, execute a simple query to retrieve
      // all persisted Cell objects and output the number of objects returned.
      String query = "SELECT FROM " + Cell.class.getName();
      List<Cell> cells = (List<Cell>) pm.newQuery(query).execute();

      resp.getWriter().print(cells.size());
    } else {
      // Retrieve a MemcacheService instance for caching objects in memory. For
      // this application, we cache the colors associated with cell positions.
      // This lets us avoid having to hit the datastore with every request
      MemcacheService memcacheService =
        MemcacheServiceFactory.getMemcacheService();

      String cachedValue = (String) memcacheService.get(cell);

      if (cachedValue != null) {
        // If the cache has an entry for the given position, output it
        resp.getWriter().print(cachedValue);
      } else {
        // If the cache does NOT have an entry associated with the position,
        // execute a query to retrieve the corresponding Cell object, then
        // output it and cache it for future requests.
        String query = "SELECT FROM " + Cell.class.getName() +
          " WHERE position == " + cell;
        List<Cell> cells = (List<Cell>) pm.newQuery(query).execute();

        if (cells.size() >= 1) {
          Cell c = cells.get(0);
          memcacheService.put(c.getPosition(), c.getColor());
          resp.getWriter().print(c.getColor());
        }
      }
    }

   // Close the PersistenceManager so it can be used by other servlets.
   pm.close();
  }
}
