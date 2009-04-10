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

import com.google.appengine.api.mail.MailService;

import java.io.IOException;

/**
 * A stub for MailService.
 *
 * @author zoso@google.com (Anirudh Dewani)
 */
public class MailServiceStub implements MailService {

  /**
   * Stubs a mail send call and validates the Message.
   *
   * @param message the mail message to be sent
   * @throws IOException Mail transport error, if any
   */
  public void send(Message message) throws IOException {
    if (message == null) {
      throw new IOException();
    }
  }

  /**
   * Stubs a mail send call and validates the Message.
   *
   * @param message the mail message to be sent
   * @throws IOException Mail transport error, if any
   */
  public void sendToAdmins(Message message) throws IOException {
    if (message == null) {
      throw new IOException();
    }
  }
}
