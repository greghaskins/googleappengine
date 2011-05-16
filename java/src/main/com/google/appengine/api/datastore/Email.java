/*
 * Copyright 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.appengine.api.datastore;

import java.io.Serializable;

/**
 * An RFC2822 email address. Makes no attempt at validation.
 *
 */
public final class Email implements Serializable, Comparable<Email> {

  public static final long serialVersionUID = -4807513785819575482L;

  private String email;

  public Email(String email) {
    if (email == null) {
      throw new NullPointerException("email must not be null");
    }
    this.email = email;
  }

  /**
   * This constructor exists for frameworks (e.g. Google Web Toolkit)
   * that require it for serialization purposes.  It should not be
   * called explicitly.
   */
  @SuppressWarnings("unused")
  private Email() {
    this.email = null;
  }

  public String getEmail() {
    return email;
  }

  public int compareTo(Email e) {
    return email.compareTo(e.email);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Email email1 = (Email) o;

    if (!email.equals(email1.email)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return email.hashCode();
  }
}
