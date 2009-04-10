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

import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.ADD_RESULT;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.APP_CONTEXT_PATH;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.AS_BEAN;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.CAR_USER;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.GETALL_RESULT;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_MAKE;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_MILEAGE;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_MODEL;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_PRICE;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_TYPE;
import static com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData.TEST_YEAR;

import com.google.appengine.demos.autoshoppe.AutoShoppeConstants;
import com.google.appengine.demos.autoshoppe.AutoShoppeException;
import com.google.appengine.demos.autoshoppe.AutoShoppeService;
import com.google.appengine.demos.autoshoppe.Vehicle;

import junit.framework.TestCase;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Collection;

/**
 * Test class for {@link AutoShoppeService}. DAO is stubbed out for persistence.
 *
 * @author zoso@google.com (Anirudh Dewani)
 */
public class AutoShoppeServiceTest extends TestCase {

  private ApplicationContext applicationContext = null;

  /**
   * Sets up the application context.
   */
  public void setUp() {
    applicationContext = new ClassPathXmlApplicationContext(APP_CONTEXT_PATH);
  }

  @Test
  public void testAddVehicle() {
    AutoShoppeService service = (AutoShoppeService)
        applicationContext.getBean(AS_BEAN);
    Vehicle vehicle = new Vehicle();
    vehicle.setYear(Integer.parseInt(TEST_YEAR));
    vehicle.setDate(System.currentTimeMillis());
    vehicle.setType(TEST_TYPE);
    vehicle.setOwner(CAR_USER);
    vehicle.setModel(TEST_MODEL);
    vehicle.setMake(TEST_MAKE);
    vehicle.setMileage(Long.parseLong(TEST_MILEAGE));
    vehicle.setStatus(AutoShoppeConstants.CAR_STATUS_AVL);
    vehicle.setPrice(Long
        .parseLong(TEST_PRICE));
    vehicle.setBuyer("");
    vehicle.setColor("");
    vehicle.setImage("");

    assertEquals(ADD_RESULT, service.addVehicle(vehicle));
  }

  @Test
  public void testGetAllVehicles() {
    AutoShoppeService service = (AutoShoppeService)
        applicationContext.getBean(AS_BEAN);
    Collection<Vehicle> records = service.getAllVehicles(0);
    assertEquals(GETALL_RESULT, records.size());
  }

  @Test
  public void testMarkSold() {
    AutoShoppeService service = (AutoShoppeService)
        applicationContext.getBean(AS_BEAN);
    boolean result = service.markSold(
        ADD_RESULT, CAR_USER);
    assertTrue(result);
  }

  @Test
  public void testMarkSold_throwsExceptionOnError() {
    AutoShoppeService service = (AutoShoppeService)
        applicationContext.getBean(AS_BEAN);
    try {
      service.markSold(null, CAR_USER);
      fail();
    }
    catch (NullPointerException npe) {
      assertNotNull(npe);
    }
  }

  @Test
  public void testGetVehiclesByCustomSearch() throws AutoShoppeException{
    AutoShoppeService service = (AutoShoppeService)
        applicationContext.getBean(AS_BEAN);

    Collection<Vehicle> records = service.getVehiclesByCustomSearch(
        1, AutoShoppeConstants.Operator.GREATER_THAN_EQUALS, null, 1);
    assertEquals(GETALL_RESULT, records.size());
  }

  @Test
  public void testGetVehiclesByCustomSearch_throwsExceptionOnError() {
    AutoShoppeService service = (AutoShoppeService)
        applicationContext.getBean(AS_BEAN);
    //Not a happy flow since none of the filters are valid.
    try {
      service.getVehiclesByCustomSearch(0, AutoShoppeConstants.Operator.EQUALS,
          null, 1);
      fail();
    } catch (AutoShoppeException ex) {
      assertNotNull(ex.getMessage());
    }
  }
}
