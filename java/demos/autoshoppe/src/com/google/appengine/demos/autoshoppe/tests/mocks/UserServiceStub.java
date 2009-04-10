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

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.User;
import com.google.appengine.demos.autoshoppe.tests.AutoShoppeTestData;

/**
 * A stub for AppEngine's UserService.
 *
 * @author zoso@google.com (Anirudh Dewani)
 */
public class UserServiceStub implements UserService{
  
  public String createLoginURL(String redirectPath) {
    return redirectPath + "/DummyLoginUrl";
  }
  
  public String createLogoutURL(String s) {
    return s + "/DummyLogoutUrl";
  }

  public boolean isUserLoggedIn() {
    return true;
  }

  public boolean isUserAdmin() {
    return false;
  }

  public User getCurrentUser() {
    return new User(AutoShoppeTestData.CAR_USER,
        AutoShoppeTestData.CAR_USER);
  }

}
