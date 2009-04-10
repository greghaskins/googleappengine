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

package com.google.appengine.demos.oauth;

/**
 * Information on each step of the OAuth wizard.
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public enum WizardStep {
  LOAD_DATA("/LoadData", "/loaddata.jsp", "Load Data", null),
  RECEIVE_TOKEN("/ReceiveToken", "/receivetoken.jsp", "Receive Token",
      LOAD_DATA),
  REDIRECT_TO_GOOGLE("/RedirectToGoogle", "/redirecttogoogle.jsp",
      "Redirect to Google", RECEIVE_TOKEN),
  SELECT_SCOPE("/SelectScope", "/selectscope.jsp", "Select Scope",
      REDIRECT_TO_GOOGLE),
  SELECT_SIGNATURE_INFO("/SelectSignatureInfo", "/selectsignatureinfo.jsp",
      "Select Signature Info", SELECT_SCOPE),
  WELCOME("/Welcome", "/welcome.jsp", "Welcome", SELECT_SIGNATURE_INFO);

  private final String servletName;
  private final String viewJsp;
  private final String displayString;
  private final WizardStep nextStep;

  WizardStep(String servletName, String viewJsp, String displayString,
      WizardStep nextStep) {
    this.servletName = servletName;
    this.viewJsp = viewJsp;
    this.displayString = displayString;
    this.nextStep = nextStep;
  }

  public String getServletName() {
    return servletName;
  }

  public String getView() {
    return viewJsp;
  }

  public WizardStep getNextStep() {
    return nextStep;
  }

  @Override
  public String toString() {
    return displayString;
  }
}
