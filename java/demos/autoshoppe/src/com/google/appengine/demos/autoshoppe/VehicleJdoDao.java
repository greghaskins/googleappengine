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

import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_STATUS;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_STATUS_AVL;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_STATUS_SOLD;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.CAR_TYPE;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.Operator;
import static com.google.appengine.demos.autoshoppe.AutoShoppeConstants.PAGE_SIZE;

import org.springframework.orm.jdo.support.JdoDaoSupport;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;

/**
 * A JDO implementation object for VehicleDao.
 *
 * @author zoso@google.com (Anirudh Dewani)
 */
public class VehicleJdoDao extends JdoDaoSupport implements VehicleDao {

  @Override
  public String create(Vehicle vehicle) {

    /*
     * Uses Spring's JdoTemplate helper since the entity's state doesn't need
     * to be changed more than once. JdoTemplate will obtain and release a
     * PersistenceManager.
     */
    getJdoTemplate().makePersistent(vehicle);
    return vehicle.getId();
  }

  @Override
  public void update(String key, String user) {
    /*
     * Uses PersistenceManager to retrieve the entity, modify the state
     * and persist again. This has to be done with the same instance of
     * PersistenceManager since the entity continues to be bound to the manager
     * unless explicitly detached.
     */
    PersistenceManager pm = getPersistenceManager();
    Vehicle vehicle = pm.getObjectById(Vehicle.class, Long.parseLong(key));
    vehicle.setBuyer(user);
    vehicle.setStatus(CAR_STATUS_SOLD);
    try {
      pm.makePersistent(vehicle);
    }
    finally {
      releasePersistenceManager(pm);
    }
  }

  @Override
  public Collection<Vehicle> getAllVehicles(int page) {
    PersistenceManager pm = getPersistenceManager();
    try {
      page = (page == 0) ? 1 : page;
      Query query = pm.newQuery(Vehicle.class);
      query.setFilter("status == pAvl");
      query.declareParameters("String pAvl");
      query.setRange(PAGE_SIZE * (page - 1), PAGE_SIZE * page);
      return pm.detachCopyAll((Collection<Vehicle>)
          query.execute(CAR_STATUS_AVL));
    }
    finally {
      releasePersistenceManager(pm);
    }
  }

  @Override
  public Collection<Vehicle> getVehiclesByCustomFilters(
      long price, Operator priceOp, String vehicleType,
      int page) {

    PersistenceManager pm = getPersistenceManager();
    Query query = pm.newQuery(Vehicle.class);
    StringBuilder filters = new StringBuilder(36);
    StringBuilder declaredParams = new StringBuilder(36);
    Map<String, Object> params = new HashMap<String, Object>();

    if (price != 0) {
      switch (priceOp) {
        case EQUALS:
          filters.append("price == pPrice");
          break;
        case LESS_THAN_EQUALS:
          filters.append("price <= pPrice");
          break;
        case GREATER_THAN_EQUALS:
          filters.append("price >= pPrice");
          break;
      }
      declaredParams.append("long pPrice");
      params.put("pPrice", price);
    }

    if (vehicleType != null) {
      if (filters.length() > 0) {
        filters.append(" && ");
        declaredParams.append(" ,");
      }
      filters.append(CAR_TYPE);
      filters.append(" == pType");
      declaredParams.append("String pType");
      params.put("pType", vehicleType);
    }

    filters.append(" && ");
    filters.append(CAR_STATUS);
    filters.append(" == pStatus");
    declaredParams.append(",String pStatus");
    params.put("pStatus", CAR_STATUS_AVL);
    query.declareParameters(declaredParams.toString());
    query.setFilter(filters.toString());
    query.setRange(PAGE_SIZE * (page - 1), PAGE_SIZE * page);
    try {
      return pm.detachCopyAll((Collection<Vehicle>)
          query.executeWithMap(params));
    }
    finally {
      releasePersistenceManager(pm);
    }
  }
}
