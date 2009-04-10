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

import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.MSG_BEAN_INIT_FAILURE;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.Operator;

import com.google.appengine.api.mail.MailService;

import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is the business service class for AutoShoppe. Provides support for
 * searching and managing vehicles listings.
 *
 * @author zoso@google.com (Anirudh Dewani)
 */
public class AutoShoppeService {

  private VehicleDao vehicleDao;
  private MailService mailService;
  private MailService.Message mailMessage;
  private static final Logger log = Logger.getLogger(
      AutoShoppeService.class.getName());

  /**
   * Spring Dependecy Injection.
   *
   * @param mailService AppEngine's MailService instance.
   */
  public void setMailService(MailService mailService) {
    this.mailService = mailService;
  }

  /**
   * Spring Dependency Injection
   *
   * @param mailMessage a message instance.
   */
  public void setMailMessage(MailService.Message mailMessage) {
    this.mailMessage = mailMessage;
  }

  /**
   * Vechicle Data Access Object is injected into service class by the
   * spring dependency injection container.
   *
   * @param vehicleDao the DAO object to be injected
   */
  public void setVehicleDao(VehicleDao vehicleDao) {
    this.vehicleDao = vehicleDao;
  }

  /**
   * Checks the bean state against it's dependencies.
   */
  public void init() {
    if (vehicleDao == null || mailMessage == null || mailService == null) {
      throw new IllegalStateException(MSG_BEAN_INIT_FAILURE +
          AutoShoppeService.class.toString());
    }
  }

  /**
   * Adds a car to the listing. A user needs to be logged in to add a
   * new car listing.
   *
   * @param vehicle a POJO with vehcile details
   * @return a unique id for the car listing
   */
  public String addVehicle(Vehicle vehicle) {
    return vehicleDao.create(vehicle).toString();
  }

  /**
   * Blocks a listed car and sets the user as the buyer. On a successful status
   * update, it also sends an email notification to the buyer.
   *
   * @param recordKey the unique key identifying the vehicle listing
   * @param email the email of the user logged in
   * @return true, if car was available and blocked
   */
  public boolean markSold(String recordKey, String email) {
    try {
      vehicleDao.update(recordKey, email);
      mailMessage.setTo(email);
      mailService.send(mailMessage);
      return true;
    } catch (AutoShoppeException re) {
      log.log(Level.SEVERE, re.getEntityKey(), re);
      return false;
    } catch (IOException e) {
      // This is a Mail API error. The car is marked as sold. 
      log.log(Level.SEVERE, email, e);
      return true;
    }
  }

  /**
   * Retrieves all the cars listed with support for pagination.
   *
   * @param page the page number of results to be retrieved
   * @return a list of all available cars as POJO's
   */
  public Collection<Vehicle> getAllVehicles(int page) {
    return vehicleDao.getAllVehicles(page);
  }

  /**
   * Returns custom search results. The parameters are car type and a
   * relational operator for a price filter.
   * e.g. type EQUALS Sedan && price LESS THAN 10000.
   *
   * @param price the value for price filter
   * @param priceOp the relational operator for price filter
   * @param vehicleType the value for vehicle type filter
   * @param page the page number to be fetched
   * @return a list of filtered Car records
   */
  public Collection<Vehicle> getVehiclesByCustomSearch(
      long price, Operator priceOp, String vehicleType, int page) {
    return vehicleDao.getVehiclesByCustomFilters(
        price, priceOp, vehicleType, page);
  }

}
