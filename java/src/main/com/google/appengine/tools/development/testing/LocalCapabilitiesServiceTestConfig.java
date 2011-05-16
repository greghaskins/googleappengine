// Copyright 2009 Google Inc. All Rights Reserved.
package com.google.appengine.tools.development.testing;

import com.google.appengine.api.capabilities.dev.LocalCapabilitiesService;

/**
 * Config for accessing the local capabilities service in tests.
 *
 */
public class LocalCapabilitiesServiceTestConfig implements LocalServiceTestConfig {

  @Override
  public void setUp() {
  }

  @Override
  public void tearDown() {
  }

  public static LocalCapabilitiesService getLocalCapabilitiesService() {
    return (LocalCapabilitiesService) LocalServiceTestHelper
        .getLocalService(LocalCapabilitiesService.PACKAGE);
  }
}
