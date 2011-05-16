// Copyright 2009 Google Inc. All rights reserved.

package com.google.appengine.api.blobstore;

/**
 * Creates {@link BlobstoreService} implementations.
 *
 */
public final class BlobstoreServiceFactory {

  /**
   * Creates a {@code BlobstoreService}.
   */
  public static BlobstoreService getBlobstoreService() {
    return new BlobstoreServiceImpl();
  }
}
