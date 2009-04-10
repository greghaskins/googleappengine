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
package com.google.appengine.demos.autoshoppe.tests.mocks;

import com.google.appengine.demos.autoshoppe.AutoShoppeConstants;
import com.google.appengine.demos.autoshoppe.AutoShoppeException;
import com.google.appengine.demos.autoshoppe.Vehicle;
import com.google.appengine.demos.autoshoppe.VehicleDao;

import com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A stub vehicle DAO. Validates incoming parameters and answers calls
 * with canned responses.
 *
 * @author zoso@google.com (Anirudh Dewani)
 */
public class VehicleDaoStub implements VehicleDao {

  /**
   * Mocks an add car operation. A user needs to be logged in to add a
   * new car listing.
   *
   * @param vehicle a POJO with vehicle details
   * @return a unique id for the car listing
   */
  public String create(Vehicle vehicle) {
    String dummyCreated = null;
    boolean params = (vehicle.getYear() != 0) && (vehicle.getModel() != null) &&
        (vehicle.getMake() != null) && (vehicle.getType() != null) &&
        (vehicle.getMileage() != 0) && (vehicle.getPrice() != 0) &&
        (vehicle.getOwner().equals(AutoShoppeTestData.CAR_USER));
    if (!params) {
      dummyCreated = null;
    } else {
      dummyCreated = AutoShoppeTestData.ADD_RESULT;
    }
    return dummyCreated;
  }

  /**
   * Mocks the update car in the listing operation.
   *
   * @param key  Unique ID of the entity.
   * @param user The logged in user.
   */
  public void update(String key, String user) {
    if (!(key != null && user.equals(AutoShoppeTestData.CAR_USER))) {
      throw new AutoShoppeException("failed", key.toString());
    }
  }

  /**
   * Retrieves all entities from the datastore.
   *
   * @return a list of all Car entities.
   */
  public Collection<Vehicle> getAllVehicles(int page) {
    return createRecords();
  }

  /**
   * Returns custom search results. The parameters are car type and
   * a relational operator for a price filter.
   * e.g. type EQUALS Sedan && price LESS THAN 10000.
   *
   * @param price the value for price filter
   * @param priceOp the relational operator for price filter
   * @param vehicleType the value for vehicle type filter
   * @param page the page to fetch
   * @return a list of filtered Car entities
   */
  public Collection<Vehicle> getVehiclesByCustomFilters(
      long price, AutoShoppeConstants.Operator priceOp, String vehicleType,
      int page) {
    Collection<Vehicle> records = null;
    // If invalid input is recieved throw exception for tests.
    if (price == 0 && vehicleType == null) {
      throw new AutoShoppeException("Invalid input", null);
    } else {
      records = createRecords();
    }
    return records;
  }

  /**
   * Create mock records that look like real records but do not have a
   * persistence key attached.
   *
   * @return a collection of vehicle records
   */
  private Collection<Vehicle> createRecords() {
    Collection<Vehicle> records = new ArrayList<Vehicle>();

    for (int i = 0; i < 10; i++) {
      Vehicle vehicle = new Vehicle();
      vehicle.setYear(2008);
      vehicle.setDate(System.currentTimeMillis());
      vehicle.setType("Sedan");
      vehicle.setOwner(AutoShoppeConstants.CAR_OWNER);
      vehicle.setModel("Civic");
      vehicle.setMake("Honda");
      vehicle.setMileage(20000);
      vehicle.setStatus(AutoShoppeConstants.CAR_STATUS_AVL);
      vehicle.setBuyer("");
      vehicle.setColor("");
      vehicle.setImage("");
      vehicle.setPrice(20000);
      records.add(vehicle);
    }
    return records;
  }
}
