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

import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.Operator;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.APP_CONTEXT_PATH;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.APP_DOMAIN;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.APP_VERSION;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.CAR_USER;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.DS_STORE_PROP;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.GETALL_RESULT;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.JDO_DAO_BEAN;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_MAKE;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_MILEAGE;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_MODEL;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_PRICE;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_TYPE;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_YEAR;

import com.google.appengine.demos.autoshoppe.Vehicle;
import com.google.appengine.demos.autoshoppe.VehicleDao;
import com.google.appengine.tools.development.ApiProxyLocalFactory;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.apphosting.api.ApiProxy;

import junit.framework.TestCase;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * Tests for {@link com.google.appengine.demos.autoshoppe.VehicleJdoDao}.
 * Creates a proxy delegate for establishing a local APIProxy Environment.
 *
 * @author zoso@google.com (Anirudh Dewani)
 */
public class VehicleDataStoreDaoTest extends TestCase {

  private ApplicationContext applicationContext;

  /**
   * Static inner class for an API proxy test environment implementation.
   */
  private static class TestAPIEnvironment implements ApiProxy.Environment {

    public String getAppId() {
      return AutoShoppeTestData.APP_NAME;
    }

    public void setDefaultNamespace(String s) {
    }

    public String getRequestNamespace() {
      return "com.google.appengine.autoshoppe";
    }

    public String getDefaultNamespace() {
      return "com.google.appengine.autoshoppe";
    }

    public String getVersionId() {
      return APP_VERSION;
    }

    public String getAuthDomain() {
      return APP_DOMAIN;
    }

    public boolean isLoggedIn() {
      return false;
    }

    public String getEmail() {
      return null;
    }

    public boolean isAdmin() {
      return false;
    }

  }

  /**
   * Set up the application context and test environment.
   */
  public void setUp() {
    ApiProxyLocalFactory factory = new ApiProxyLocalFactory();
    factory.setApplicationDirectory(new File("."));
    ApiProxyLocal delegate = factory.create();
    delegate.setProperty(DS_STORE_PROP, "true");
    ApiProxy.setDelegate(delegate);
    ApiProxy.setEnvironmentForCurrentThread(new TestAPIEnvironment());

    // Initialize the spring context for tests.
    applicationContext = new ClassPathXmlApplicationContext(APP_CONTEXT_PATH);
  }

  /**
   * Clears ThreadLocal.
   */
  public void tearDown() {
    ApiProxy.clearEnvironmentForCurrentThread();
  }

  @Test
  public void testCreate() {
    VehicleDao dao = (VehicleDao) applicationContext.getBean(JDO_DAO_BEAN);
    assertNotNull(createVehicles(dao, 1).get(0));
  }

  @Test
  public void testGetAllVehicles() {
    VehicleDao dao = (VehicleDao) applicationContext.getBean(
        AutoShoppeTestData.JDO_DAO_BEAN);
    createVehicles(dao, GETALL_RESULT);
    Collection<Vehicle> results = dao.getAllVehicles(0);
    assertEquals(results.size(), GETALL_RESULT);
  }

  @Test
  public void testUpdate() throws Exception {
    VehicleDao dao = (VehicleDao) applicationContext.getBean(JDO_DAO_BEAN);
    String key = createVehicles(dao, 1).get(0);
    dao.update(key, CAR_USER);
  }

  @Test
  public void testGetVehiclesByCustomFilters() {
    VehicleDao dao = (VehicleDao) applicationContext.getBean(JDO_DAO_BEAN);
    createVehicles(dao, GETALL_RESULT);
    Collection<Vehicle> results = dao.getVehiclesByCustomFilters(
        1, Operator.GREATER_THAN_EQUALS, null, 1);
    assertEquals(GETALL_RESULT, results.size());
  }

  /**
   * Creates specified number of entities in the in-memory datastore.
   *
   * @param dao   data access object for persisting entities
   * @param count number of vehicle entities to create
   * @return List of entity keys
   */
  public List<String> createVehicles(VehicleDao dao, int count) {
    List<String> vehicleKeys = new ArrayList<String>();
    for (int i = 0; i < count; i++) {
      Vehicle vehicle = new Vehicle();
      vehicle.setYear(Integer.parseInt(TEST_YEAR));
      vehicle.setDate(System.currentTimeMillis());
      vehicle.setType(TEST_TYPE);
      vehicle.setOwner(CAR_USER);
      vehicle.setModel(TEST_MODEL);
      vehicle.setMake(TEST_MAKE);
      vehicle.setMileage(Long.parseLong(TEST_MILEAGE));
      vehicle.setPrice(Long.parseLong(TEST_PRICE));
      vehicle.setStatus("avl");
      vehicle.setBuyer("");
      vehicle.setColor("");
      vehicle.setImage("");
      vehicleKeys.add(dao.create(vehicle));
    }
    return vehicleKeys;
  }

}
