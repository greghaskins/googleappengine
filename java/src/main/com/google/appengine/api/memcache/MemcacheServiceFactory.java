// Copyright 2007 Google Inc. All rights reserved.

package com.google.appengine.api.memcache;

/**
 * The factory by which users acquire a handle to the MemcacheService.
 *
 */
public class MemcacheServiceFactory {
  /**
   * Gets a handle to the cache service.  Although there is only one actual
   * cache, an application may make as many {@code MemcacheService} instances
   * as it finds convenient.
   * <p>
   * If using multiple instances, note that the error handler established with
   * {@link MemcacheService#setErrorHandler(ErrorHandler)} is specific to each
   * instance.
   *
   * All operations in the {@code MemcacheService} will use the current
   * namespace provided by
   * {@link com.google.appengine.api.NamespaceManager#get()}.
   *
   * @return a new {@code MemcacheService} instance.
   */
  public static MemcacheService getMemcacheService() {
    return new MemcacheServiceImpl(null);
  }

  /**
   * Gets a handle to the cache service, forcing use of specific namespace.
   * The method returns {@code MemcacheService}
   * similar to the one returned by
   * {@link MemcacheServiceFactory#getMemcacheService()}
   * but it will use specified {@code namespace} for all operations.
   *
   * @param namespace if not {@code null} forces the use of {@code namespace}
   * for all operations in {@code MemcacheService} . If {@code namespace} is
   * {@code null} - created {@MemcacheService} will use current namespace
   * provided by {@link com.google.appengine.api.NamespaceManager#get()}.
   *
   * @return a new {@code MemcacheService} instance.
   */
  public static MemcacheService getMemcacheService(String namespace) {
    return new MemcacheServiceImpl(namespace);
  }

  private MemcacheServiceFactory() {
  }
}
