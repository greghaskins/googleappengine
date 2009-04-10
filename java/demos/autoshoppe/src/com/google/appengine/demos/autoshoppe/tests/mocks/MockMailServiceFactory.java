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
 */

package com.google.appengine.demos.autoshoppe.tests.mocks;

import com.google.appengine.api.mail.MailService;

/**
 * Mock factory for MailService.
 * Spring's factory method creational pattern allows to replace a real object
 * factory with a mock by providing the same factory method that instead
 * creates a stub implementation of {@link MailService}.
 * 
 * @author zoso@google.com (Anirudh Dewani)
 */
public class MockMailServiceFactory {

  private static MailService mailService;

  private MockMailServiceFactory() {
  }

  public static MailService getMailService() {
    // Mock needn't worry of multithreaded access.
    if (mailService == null) {
      mailService = new MailServiceStub();
    }
    return mailService;
  }
}
