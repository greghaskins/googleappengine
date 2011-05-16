// Copyright 2008 Google Inc. All Rights Reserved.
package com.google.appengine.api.memcache;

/**
 * A strict error handler, which will throw {@link MemcacheServiceException}
 * for any service error condition.
 *
 */
public class StrictErrorHandler implements ErrorHandler {

  /**
   * Throws {@link InvalidValueException} for any call.
   *
   * @param t the classpath error exception
   * @throws MemcacheServiceException for any service error.
   */
  public void handleDeserializationError(InvalidValueException t) {
    throw t;
  }

  /**
   * Throws {@link MemcacheServiceException} for any call.
   *
   * @param t the service error exception
   * @throws MemcacheServiceException for any service error.
   */
  public void handleServiceError(MemcacheServiceException t) {
    throw t;
  }
}
