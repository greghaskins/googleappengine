// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.apphosting.utils.config.BackendsXml;

import java.io.File;
import java.util.Map;

/**
 * Null implementation of the BackgroundContainer interface.
 *
 *
 */
public class NullBackgroundContainer implements BackgroundContainer {

  @Override
  public void setServiceProperties(Map<String, String> properties) {
  }

  @Override
  public void shutdownAll() throws Exception {
  }

  @Override
  public void startupAll(BackendsXml backendsXml) throws Exception {
  }

  @Override
  public void init(File appDir, String appEngineWebXml, String address) {
  }

}
