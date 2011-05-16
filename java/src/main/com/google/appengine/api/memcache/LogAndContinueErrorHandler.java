// Copyright 2008 Google Inc. All Rights Reserved.
package com.google.appengine.api.memcache;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The default error handler, which will cause most service errors to behave
 * as though there were a cache miss, not an error.
 *
 */
public class LogAndContinueErrorHandler implements ErrorHandler {
  private final Level level;
  private final Logger logger;

  /**
   * Constructor for a given logging level.
   *
   * @param level the level at which back-end errors should be logged.
   */
  public LogAndContinueErrorHandler(Level level) {
    this.level = level;
    logger = Logger.getLogger(LogAndContinueErrorHandler.class.getName());
  }

  /**
   * Logs the {@code thrown} error condition, but does not expose it to
   * application code.
   *
   * @param thrown the classpath error exception
   */
  public void handleDeserializationError(InvalidValueException thrown) {
    logger.log(level, "Deserialization error in memcache", thrown);
  }

  /**
   * Logs the {@code thrown} error condition, but does not expose it to
   * application code.
   *
   * @param thrown the service error exception
   */
  public void handleServiceError(MemcacheServiceException thrown) {
    logger.log(level, "Service error in memcache", thrown);
  }
}
