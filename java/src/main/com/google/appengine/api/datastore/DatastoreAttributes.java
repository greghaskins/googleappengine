// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.api.datastore;

/**
 * Attributes of a datastore.
 *
 */
public final class DatastoreAttributes {
  /**
   * Indicates the type of datastore being used.
   *
   */
  public enum DatastoreType {
    UNKNOWN,
    MASTER_SLAVE,
    HIGH_REPLICATION,
  }

  private final DatastoreType datastoreType;

  DatastoreAttributes() {
    datastoreType = DatastoreApiHelper.getCurrentAppId().startsWith("s~") ?
        DatastoreType.HIGH_REPLICATION :
        DatastoreType.MASTER_SLAVE;
  }

  /**
   * Gets the datastore type.
   *
   * Only guaranteed to return something other than {@link
   * DatastoreType#UNKNOWN} when running in production and querying the current
   * app.
   */
  public DatastoreType getDatastoreType() {
    return datastoreType;
  }
}
