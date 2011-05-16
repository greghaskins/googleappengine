// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.apphosting.utils.config.BackendsXml;

import java.io.File;
import java.util.Map;

/**
 * Interface to background instances
 */
public interface BackgroundContainer {

  public void setServiceProperties(Map<String, String> properties);

  public void shutdownAll() throws Exception;

  public void startupAll(BackendsXml backendsXml) throws Exception;

  void init(File appDir, String appEngineWebXml, String address);
}
