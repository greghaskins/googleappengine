// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development.testing;

import com.google.appengine.api.search.dev.LocalSearchService;

/**
 * Config for accessing the local text search service in tests.
 *
 */
public class LocalSearchServiceTestConfig implements LocalServiceTestConfig {

  @Override
  public void setUp() {
  }

  @Override
  public void tearDown() {
  }

  public static LocalSearchService getLocalSearchService() {
    return (LocalSearchService) LocalServiceTestHelper.getLocalService(LocalSearchService.PACKAGE);
  }
}
