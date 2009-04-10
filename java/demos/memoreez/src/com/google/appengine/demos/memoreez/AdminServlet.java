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

import com.google.appengine.api.memcache.MemcacheServiceFactory;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import javax.jdo.PersistenceManager;
import javax.servlet.http.*;

/**
 * Servlet for processing various administrator tasks such as clearing and
 * repopulating the datastore.
 * 
 * @author jasonacooper
 */
public class AdminServlet extends HttpServlet {
  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {

    // Instantiate a new PersistenceManager which provides an interface for
    // interacting with App Engine's datastore, enabling queries as well as
    // addition and removal.
    PersistenceManager pm = PMF.get().getPersistenceManager();

    String op = req.getParameter("op");

    if (op.equals("repopulate")) {
      // Execute a simple query to retrieve a reference to all persisted Cell
      // objects.
      String query = "SELECT FROM " + Cell.class.getName();
      List<Cell> cells = (List<Cell>) pm.newQuery(query).execute();

      pm.deletePersistentAll(cells);

      // In this memory game, users will be challenged to locate two cells with
      // the same background color. The colors and the corresponding cell
      // positions are persisted in the datastore. 
 
      // The "colors" array stores the colors to use for the cell backgrounds.
      // The total number of cells will be double the number of color names
      // in this array.
      String[] colors =
        {"black", "blue", "brown", "green", "navy", "purple", "red", "yellow"};

      Vector<String> colorVector = new Vector<String>(colors.length*2);

      // Every color name in the array is pushed to the Vector object twice
      // since each color is associated with two cells.
      for (int i = 0; i < colors.length; i++) {
        for (int j = 0; j < 2; j++) {
          colorVector.add(colors[i]);       
        }
      }

      Random generator = new Random();

      while (colorVector.size() > 0) {
        // While the Vector object contains at least one color name, select a 
        // random element, remove it from the Vector, and use it to create a
        // new Cell instance which is then persisted in the App Engine
        // datastore.
        int randomIndex = generator.nextInt(colorVector.size());
        String color = colorVector.get(randomIndex);
        colorVector.remove(randomIndex);

        Cell c = new Cell(colorVector.size(), color);
        pm.makePersistent(c);
      }

      MemcacheServiceFactory.getMemcacheService().clearAll();
    }

    // Close the PersistenceManager so it can be used by other servlets.
    pm.close();

    // Redirect to the administrator front-end
    resp.sendRedirect("admin.jsp");
  }
}
