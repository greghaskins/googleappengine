// Copyright 2009 Google Inc. All Rights Reserved.
package com.google.appengine.tools.development.testing;

import com.google.appengine.api.memcache.MemcacheServicePb;
import com.google.appengine.api.memcache.dev.LocalMemcacheService;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.LocalRpcService;

/**
 * Config for accessing the local memcache service in tests.
 * {@link #tearDown()} wipes out all cache entries so that memcache is empty
 * at the end of every test.
 *
 */
public class LocalMemcacheServiceTestConfig implements LocalServiceTestConfig {

  public enum SizeUnit {
    BYTES(""),
    KB("K"),
    MB("M");

    private final String abbreviation;

    SizeUnit(String abbreviation) {
      this.abbreviation = abbreviation;
    }
  }
  private Long maxSize;
  private SizeUnit maxSizeUnits = SizeUnit.BYTES;

  @Override
  public void setUp() {
    ApiProxyLocal proxy = LocalServiceTestHelper.getApiProxyLocal();
    if (maxSize != null) {
      proxy.setProperty(
          LocalMemcacheService.SIZE_PROPERTY,
          String.format("%d%s", maxSize, maxSizeUnits.abbreviation));
    }
  }

  @Override
  public void tearDown() {
    MemcacheServicePb.MemcacheFlushRequest request =
        MemcacheServicePb.MemcacheFlushRequest.newBuilder().build();
    LocalRpcService.Status status = new LocalRpcService.Status();
    getLocalMemcacheService().flushAll(status, request);
  }

  public Long getMaxSize() {
    return maxSize;
  }

  public SizeUnit getMaxSizeUnits() {
    return maxSizeUnits;
  }

  /**
   * Sets the maximum size of the cache
   * @param maxSize
   * @param maxSizeUnits
   * @return {@code this} (for chaining)
   */
  public LocalMemcacheServiceTestConfig setMaxSize(long maxSize, SizeUnit maxSizeUnits) {
    this.maxSize = maxSize;
    this.maxSizeUnits = maxSizeUnits;
    return this;
  }

  public static LocalMemcacheService getLocalMemcacheService() {
    return (LocalMemcacheService) LocalServiceTestHelper.getLocalService(
        LocalMemcacheService.PACKAGE);
  }
}
