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

import java.util.Collection;

/**
 * A data access object specification for Car entity.
 *
 * @author zoso@google.com (Anirudh Dewani)
 */
public interface VehicleDao {

  /**
   * Adds a car to the listing. A user needs to be logged in to add a
   * new car listing. The id attribute is auto-generated and is expected to be
   * null in the vehicle instance to be persisted.
   *
   * @param vehicle a POJO with car details
   * @return a unique id for the car listing
   */
  public String create(Vehicle vehicle);

  /**
   * Updates the car in the listing by setting the status as sold.
   *
   * @param key unique ID of the entity
   * @param user the logged in user
   */
  public void update(String key, String user);

  /**
   * Retrieves all entities from the datastore.
   *
   * @param page page number of the recordset to be retrieved
   * @return a list of all Car entities
   */
  public Collection<Vehicle> getAllVehicles(int page);

  /**
   * Returns custom search results. The parameters are car type and a relational
   * operator for the price filter.
   * e.g. type EQUALS Sedan && price LESS THAN 10000.
   *
   * @param price the value for price filter
   * @param priceOp the relational operator for price filter
   * @param vehicleType the value for vehicle type filter
   * @param page the page number to fetch
   * @return a list of filtered Car entities
   */
  public Collection<Vehicle> getVehiclesByCustomFilters(
      long price, AutoShoppeConstants.Operator priceOp, String vehicleType,
      int page);

}
