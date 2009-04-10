package com.google.appengine.demos.somefamiliarhaunts;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Receives Ajax requests to add, remove, and list locations entered by users.
 *
 * @author j.s@google.com (Jeff Scudder)
 */
public class RPCHandler extends HttpServlet {

  public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    String action = req.getParameter("action");

    if (action == null) {
      return;
    }

    try {
      if (action.equals("GetLocations")) {
        res.setContentType("application/json");
        String userEmail = req.getParameter("arg0");
        // Remove the surrounding " marks from the query parameter value.
        userEmail = userEmail.substring(1, userEmail.length()-1);
        res.getWriter().print(getLocations(userEmail).toString());
        return;
      } else if (action.equals("AddLocation")) {
        String lat = req.getParameter("arg0");
        String lon = req.getParameter("arg1");
        String name = req.getParameter("arg2");
        res.getWriter().print(addLocation(lat, lon, name).toString());
        return;
      } else if (action.equals("RemoveLocation")) {
        String keyId = req.getParameter("arg0");
        removeLocation(keyId);
        res.getWriter().print("true");
        return;
      }
    } catch (JSONException je) {
      throw new IOException(je);
    }
  }

  public static JSONObject addLocation(String lat, String lon, String name) 
      throws JSONException {
    Logger log = Logger.getLogger(RPCHandler.class.getName());
    UserService userService = UserServiceFactory.getUserService();
    User user = userService.getCurrentUser();
    DatastoreService datastoreService = 
        DatastoreServiceFactory.getDatastoreService();

    Query userQuery = new Query("User");
    userQuery.addFilter("user", Query.FilterOperator.EQUAL, user);
    try {
      Entity userEntity = datastoreService.prepare(userQuery).asSingleEntity();
      if (userEntity == null && user != null) {
        userEntity = new Entity("User");
        userEntity.setProperty("user", user);
        datastoreService.put(userEntity);
      }
    } catch (PreparedQuery.TooManyResultsException re) {
      log.warning("More than one User entity for " + user.getEmail());
    }

    Entity locationEntity = new Entity("Location");
    JSONObject locationJson = new JSONObject();
    locationEntity.setProperty("user", user.getEmail());
    locationJson.put("user", user.getEmail());
    locationEntity.setProperty("latd", lat);
    locationJson.put("latd", lat);
    locationEntity.setProperty("longd", lon);
    locationJson.put("longd", lon);
    locationEntity.setProperty("name", name);
    locationJson.put("name", name);

    // Store the new location.
    datastoreService.put(locationEntity);
    // Add the location's key to the JSON we send back to the browser.
    locationJson.put("key", locationEntity.getKey().getId());
    return locationJson;
  }

  public static boolean removeLocation(String keyId) throws JSONException {
    DatastoreService datastoreService = 
        DatastoreServiceFactory.getDatastoreService();
    long id = Long.parseLong(keyId);
    datastoreService.delete(KeyFactory.createKey("Location", id));
    return true;
  }

  public static JSONArray getLocations(String userEmail) throws JSONException {
    if (userEmail == null) {
      UserService userService = UserServiceFactory.getUserService();
      User user = userService.getCurrentUser();
      if (user != null) {
        userEmail = user.getEmail();
      }
    }
    
    JSONArray locations = new JSONArray();
    if (userEmail != null) {
      DatastoreService datastoreService = 
          DatastoreServiceFactory.getDatastoreService();
      Query locationQuery = new Query("Location");
      locationQuery.addFilter("user", Query.FilterOperator.EQUAL, userEmail);

      for (Entity location : datastoreService.prepare(locationQuery)
          .asIterable()) {
        JSONObject aLocation = new JSONObject();
        aLocation.put("user", userEmail);
        aLocation.put("latd", location.getProperty("latd"));
        aLocation.put("longd", location.getProperty("longd"));
        aLocation.put("name", location.getProperty("name"));
        aLocation.put("key", location.getKey().getId());
        locations.put(aLocation);
      }
    }
    return locations;
  }
}
