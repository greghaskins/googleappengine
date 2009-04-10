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

import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.AUTH_CONTINUE_URL;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.AUTH_LOGIN;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.AUTH_LOGOUT;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.AUTH_USER;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_EXPRICE;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_KEY;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_KEY_PATTERN;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_MAKE;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_MILEAGE;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_MODEL;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_STATUS_AVL;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_TYPE;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_YEAR;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.GQL_PRICEOP;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.MSG_INVALID_NUMBER;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.MSG_INVALID_SEARCH;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.MSG_MISSING_PARAM;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.JSON_MODEL;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.JSON_VIEW;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.MSG_BEAN_INIT_FAILURE;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.Operator;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.PAGE_PARAM;

import com.google.appengine.api.users.UserService;
import com.google.gson.Gson;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.multiaction.MultiActionController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A Spring multiaction controller for mapping URLs to method names. This class
 * parses the request parameters and sets up the asynchronous JSON views for the
 * AJAX client.
 *
 * @author zoso@google.com (Anirudh Dewani)
 */
public class AutoController extends MultiActionController {

  private AutoShoppeService autoShoppeService;
  private Gson gson;
  private UserService userService;

  /**
   * Spring injected dependency using the setter.
   *
   * @param userService AppEngine UserService
   */
  public void setUserService(UserService userService) {
    this.userService = userService;
  }

  /**
   * Spring injected dependency using the setter.
   *
   * @param autoShoppeService service class for AutoShoppe
   */
  public void setAutoShoppeService(AutoShoppeService autoShoppeService) {
    this.autoShoppeService = autoShoppeService;
  }

  /**
   * Spring injected dependency using the setter.
   * The gson transformer spits JSON output from java objects.
   *
   * @param gson a JSON-Java converter
   */
  public void setGson(Gson gson) {
    this.gson = gson;
  }

  /**
   * Checks the bean state against it's dependencies. init() is set as the
   * default-init-method for all spring configured beans.
   */
  public void init() {
    if (userService == null || gson == null || autoShoppeService == null) {
      throw new IllegalStateException(MSG_BEAN_INIT_FAILURE +
          AutoController.class.toString());
    }
  }

  /**
   * Checks user authentication status by querying the UserService API
   * and sets up login and logout URLs for the client. A user with an existing
   * session is identified and automatically logged in.
   *
   *
   * @param request HttpServletRequest object
   * @param response HttpServletResponse object
   * @return ModelAndView a Spring MVC framework instance with data and view
   *         for the client
   */
  public ModelAndView login(
      HttpServletRequest request, HttpServletResponse response) {

    String loginUrl = userService.createLoginURL(AUTH_CONTINUE_URL);
    String logOutUrl = userService.createLogoutURL(AUTH_CONTINUE_URL);
    String user = userService.isUserLoggedIn()
        ? userService.getCurrentUser().getEmail() : "";

    Map<String, String> loginDataMap = new HashMap<String, String>();
    loginDataMap.put(AUTH_LOGIN, loginUrl);
    loginDataMap.put(AUTH_LOGOUT, logOutUrl);
    loginDataMap.put(AUTH_USER, user);
    return constructModelAndView(gson.toJson(loginDataMap));
  }

  /**
   * Adds a new vehicle to the datastore and returns a JSON model and view to
   * the client. In case of invalid input, an error message is sent.
   *
   * @param request HttpServletRequest object
   * @param response HttpServletResponse object
   * @return ModelAndView a Spring MVC framework instance with data and view
   *         for the client
   */
  public ModelAndView addVehicle(
      HttpServletRequest request, HttpServletResponse response) {

    if (!userService.isUserLoggedIn()) {
      ModelAndView mv = new ModelAndView();
      mv.setView(new RedirectView(
          userService.createLoginURL(request.getRequestURI())));
      return mv;
    }

    try {
      Vehicle vehicle = new Vehicle();
      vehicle.setYear(Integer.parseInt(request.getParameter(CAR_YEAR)));
      vehicle.setDate(System.currentTimeMillis());
      vehicle.setType(request.getParameter(CAR_TYPE));
      vehicle.setOwner(userService.getCurrentUser().getEmail());
      vehicle.setModel(request.getParameter(CAR_MODEL));
      vehicle.setMake(request.getParameter(CAR_MAKE));
      vehicle.setMileage(Long.parseLong(request.getParameter(CAR_MILEAGE)));
      vehicle.setStatus(CAR_STATUS_AVL);
      vehicle.setPrice(Long.parseLong(request.getParameter(CAR_EXPRICE)));
      vehicle.setBuyer("");
      vehicle.setColor("");
      vehicle.setImage("");

      return constructModelAndView(
          CAR_KEY_PATTERN + autoShoppeService.addVehicle(vehicle));

    } catch (NumberFormatException nfe) {
      //Parsing failed over expected numeric data.
      return constructModelAndView(MSG_INVALID_NUMBER);
    } catch (NullPointerException npe) {
      //A required request parameter is missing.
      return constructModelAndView(MSG_MISSING_PARAM);
    }
  }

  /**
   * Retrieves all the vehicles from the datastore and returns them
   * formatted as Json.
   *
   * @param request HttpServletRequest object
   * @param response HttpServletResponse object
   * @return ModelAndView a Spring MVC framework instance with data and view
   *         for the client
   */
  public ModelAndView getAllVehicles(
      HttpServletRequest request, HttpServletResponse response) {
    //Default page is one. If request contains one, use that else default.
    int page = 1;
    Object parameter = request.getParameter(PAGE_PARAM);
    if (parameter != null) {
      try {
        page = Integer.parseInt(parameter.toString());
      } catch (NumberFormatException nfe) {
        page = 1;
      }
    }
    Collection<Vehicle> records = autoShoppeService.getAllVehicles(page);
    return constructModelAndView(gson.toJson(records));
  }

  /**
   * Searches with custom filters on vehicle selection and returns the
   * reponse in Json format.  When a filter on price is specified, the parameter
   * values of GQL_PRICEOP are interpreted as follows:
   * 0 - EQUALS
   * 1 - LESS THAN EQUALS
   * 2 - GREATER THAN EQUALS
   *
   * Sends back an appropriate error message in case of invalid input.
   *
   * @param request HttpServletRequest object
   * @param response HttpServletResponse object
   * @return ModelAndView a Spring MVC framework instance with data and view
   *         for the client
   */

  public ModelAndView getVehiclesByCustomSearch(
      HttpServletRequest request, HttpServletResponse response) {

    //Default search filters.
    long price = 0;
    Operator priceOp = Operator.EQUALS;
    String vehicleType = null;
    int page = 1;

    //Retrieve all the paramaeters from POST request.
    String parameter = request.getParameter(CAR_EXPRICE);
    if (parameter != null) {

      //Validate the price filter and operator.
      try {
        price = Integer.parseInt(parameter);
        parameter = request.getParameter(GQL_PRICEOP);
        priceOp = lookUpOperatorByOrdinal(Integer.parseInt(parameter));
      } catch (NumberFormatException nfe) {
        //Parsing failed over expected numeric data.
        return constructModelAndView(MSG_INVALID_NUMBER);
      } catch (NullPointerException npe) {
        //Required request parameter is missing.
        return constructModelAndView(MSG_MISSING_PARAM);
      } catch (ArrayIndexOutOfBoundsException indexException) {
        //Invalid search criteria
        return constructModelAndView(MSG_INVALID_SEARCH);
      }
    }

    parameter = request.getParameter(CAR_TYPE);
    if (parameter != null) {
      vehicleType = parameter;
    }

    if (vehicleType == null && price == 0) {
      //One of the filters has to be specified
      return constructModelAndView(MSG_INVALID_SEARCH);
    }

    parameter = request.getParameter(PAGE_PARAM);
    if (parameter != null) {
      try {
        page = Integer.parseInt(parameter);
      } catch (NumberFormatException nfe) {
        //Defaulting the page to 1.
        page = 1;
      }
    }

    Collection<Vehicle> records = autoShoppeService.getVehiclesByCustomSearch(
        price, priceOp, vehicleType, page);
    return constructModelAndView(gson.toJson(records));
  }

  /**
   * Updates the vehicle status as sold with the buyer email address.
   *
   * @param request HttpServletRequest object
   * @param response HttpServletResponse object
   * @return ModelAndView a Spring MVC framework instance with data and view
   *         for the client
   */
  public ModelAndView markSold(
      HttpServletRequest request, HttpServletResponse response) {
    String recordKey = request.getParameter(CAR_KEY);
    if (recordKey == null) {
      return constructModelAndView(MSG_MISSING_PARAM);
    }
    
    boolean success = autoShoppeService.markSold(recordKey,
        userService.getCurrentUser().getEmail());
    return constructModelAndView(String.valueOf(success));
  }

  /**
   * Looks up the Operator value based on ordinal.
   * 
   * @param ordinal the ordinal value to look up in enum set
   * @return Operator value corresponding to the ordinal
   */
  private static Operator lookUpOperatorByOrdinal(int ordinal) {
    return Operator.values()[ordinal];
  }

  /**
   * Creates a Spring ModelAndView respresentation from the JSON formatted
   * result.
   *
   * @param json the data model in MVC.
   * @return ModelAndView a Spring MVC framework instance with data and view
   *         for the client
   */
  private static ModelAndView constructModelAndView(String json) {
    ModelAndView mv = new ModelAndView();
    mv.addObject(JSON_MODEL, json);
    mv.setViewName(JSON_VIEW);
    return mv;
  }
}
