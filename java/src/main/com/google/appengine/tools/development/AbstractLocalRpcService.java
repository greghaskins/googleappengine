// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import java.util.Map;

/**
 * An abstract implementation of {@link LocalRpcService} which runs no
 * setup logic and provides no deadline hints.
 *
 */
public abstract class AbstractLocalRpcService implements LocalRpcService {

  public void init(LocalServiceContext context, Map<String, String> properties) {
  }

  public void start() {
  }

  public void stop() {
  }

  public Double getDefaultDeadline(boolean isOfflineRequest) {
    return null;
  }

  public Double getMaximumDeadline(boolean isOfflineRequest) {
    return null;
  }

  public Integer getMaxApiRequestSize() {
    return null;
  }
}
