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

package com.google.appengine.demos.autoshoppe.tests;

import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_EXPRICE;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_KEY;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_KEY_PATTERN;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_MAKE;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_MILEAGE;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_MODEL;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_TYPE;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_YEAR;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.GQL_PRICEOP;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.Operator;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.JSON_MODEL;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.JSON_VIEW;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.MSG_INVALID_SEARCH;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.AC_BEAN;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.ADD_RESULT;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.APP_CONTEXT_PATH;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.GETALL_RESULT;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.GSON_BEAN;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.LOGIN_RESULT;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_MAKE;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_MILEAGE;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_MODEL;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_PRICE;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_TYPE;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_YEAR;

import com.google.appengine.demos.autoshoppe.AutoController;
import com.google.appengine.demos.autoshoppe.AutoShoppeException;
import com.google.appengine.demos.autoshoppe.Vehicle;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import junit.framework.TestCase;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * JUnit TestCase for {@link AutoController}.
 *
 * @author zoso@google.com (Anirudh Dewani)
 */
public class AutoControllerTest extends TestCase {

  private ApplicationContext applicationContext = null;

  /**
   * Sets up the application context.
   */
  public void setUp() {
    applicationContext = new ClassPathXmlApplicationContext(APP_CONTEXT_PATH);
  }

  @Test
  public void testLogin() {
    AutoController controller = (AutoController)
        applicationContext.getBean(AC_BEAN);
    MockHttpServletRequest request = null;
    MockHttpServletResponse response = null;
    ModelAndView mv = controller.login(request, response);
    assertEquals(LOGIN_RESULT, mv.getModel().get(JSON_MODEL));
    assertEquals(JSON_VIEW, mv.getViewName());
  }

  @Test
  public void testAddVehicle() {
    AutoController controller = (AutoController)
        applicationContext.getBean(AC_BEAN);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = null;

    // Create test HttpRequest
    Map<String, Object> params = new HashMap<String, Object>();
    params.put(CAR_YEAR, TEST_YEAR);
    params.put(CAR_MODEL, TEST_MODEL);
    params.put(CAR_MAKE, TEST_MAKE);
    params.put(CAR_TYPE, TEST_TYPE);
    params.put(CAR_MILEAGE, TEST_MILEAGE);
    params.put(CAR_EXPRICE, TEST_PRICE);
    request.addParameters(params);

    ModelAndView mv = controller.addVehicle(request, response);
    assertEquals(CAR_KEY_PATTERN + ADD_RESULT, mv.getModel().get(JSON_MODEL));
    assertEquals(JSON_VIEW, mv.getViewName());
  }

  @Test
  public void testGetAllVehicles() {
    AutoController controller = (AutoController)
        applicationContext.getBean(AC_BEAN);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = null;
    ModelAndView mv = controller.getAllVehicles(request, response);
    Gson gson = (Gson) applicationContext.getBean(GSON_BEAN);

    //Get the count of records and assert with the expected count.
    Type collectionType = new TypeToken<Collection<Vehicle>>() {
    }.getType();
    Collection<Vehicle> records = gson.fromJson(mv.getModel().get(
        JSON_MODEL).toString(), collectionType);
    assertEquals(GETALL_RESULT, records.size());
    assertEquals(JSON_VIEW, mv.getViewName());
  }

  @Test
  public void testMarkSold() {
    AutoController controller = (AutoController)
        applicationContext.getBean(AC_BEAN);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = null;
    request.setParameter(CAR_KEY, ADD_RESULT);
    ModelAndView mv = controller.markSold(request, response);
    assertTrue(Boolean.valueOf(mv.getModel().get(JSON_MODEL).toString()));
    assertEquals(JSON_VIEW, mv.getViewName());
  }

  @Test
  public void testGetVehiclesByCustomSearch() throws AutoShoppeException {
    AutoController controller = (AutoController)
        applicationContext.getBean(AC_BEAN);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = null;

    request.setParameter(CAR_EXPRICE, TEST_YEAR);
    request.setParameter(GQL_PRICEOP,
        String.valueOf(Operator.EQUALS.ordinal()));

    ModelAndView mv = controller.getVehiclesByCustomSearch(request, response);
    Gson gson = (Gson) applicationContext.getBean(GSON_BEAN);

    //Get the count of records and assert with the expected count.
    Type collectionType = new TypeToken<Collection<Vehicle>>() {
    }.getType();
    Collection<Vehicle> records = gson.fromJson(mv.getModel().get(
        JSON_MODEL).toString(), collectionType);
    assertEquals(GETALL_RESULT, records.size());
    assertEquals(JSON_VIEW, mv.getViewName());
  }

  @Test
  public void testGetVehiclesByCustomSearch_invalidInputMessageOnError() {
    AutoController controller = (AutoController)
        applicationContext.getBean(AC_BEAN);
    //Not a happy flow since request is empty. Should generate error.
    MockHttpServletRequest request = new MockHttpServletRequest();
    ModelAndView mv = controller.getVehiclesByCustomSearch(request, null);
    assertEquals(MSG_INVALID_SEARCH, mv.getModel().get(JSON_MODEL).toString());

  }
}
