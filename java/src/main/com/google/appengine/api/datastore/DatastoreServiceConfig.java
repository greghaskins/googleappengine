// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.appengine.api.datastore;

import com.google.appengine.api.datastore.ReadPolicy.Consistency;
/**
 * User-configurable properties of the datastore.
 *
 * Notes on usage:<br>
 * The recommended way to instantiate a {@code DatastoreServiceConfig} object
 * is to statically import {@link Builder}.* and invoke a static creation
 * method followed by an instance mutator (if needed):
 *
 * <blockquote>
 * <pre>
 * import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.*;
 * import com.google.appengine.api.datastore.ReadPolicy.Consistency;
 *
 * ...
 *
 * // eventually consistent reads
 * DatastoreServiceConfig config = withReadPolicy(new ReadPolicy(Consistency.EVENTUAL));
 *
 * // eventually consistent reads with a 5 second deadline
 * DatastoreServiceConfig config =
 *   withReadPolicy(new ReadPolicy(Consistency.EVENTUAL)).deadline(5.0);
 *
 * </pre>
 * </blockquote>
 */
public final class DatastoreServiceConfig {
  /**
   * The default maximum size a request RPC can be.
   */
  static final int DEFAULT_RPC_SIZE_LIMIT_BYTES = 1024 * 1024;

  /**
   * The default maximum number of keys allowed in a Get request.
   */
  static final int DEFAULT_MAX_BATCH_GET_KEYS = 1000;

  /**
   * The default maximum number of entities allowed in a Put or Delete request.
   */
  static final int DEFAULT_MAX_BATCH_WRITE_ENTITIES = 500;

  private ImplicitTransactionManagementPolicy implicitTransactionManagementPolicy =
      ImplicitTransactionManagementPolicy.NONE;

  private ReadPolicy readPolicy = new ReadPolicy(Consistency.STRONG);

  private Double deadline;

  private int maxRpcSizeBytes = DEFAULT_RPC_SIZE_LIMIT_BYTES;
  private int maxBatchWriteEntities = DEFAULT_MAX_BATCH_WRITE_ENTITIES;
  private int maxBatchReadEntities = DEFAULT_MAX_BATCH_GET_KEYS; private Integer maxEntityGroupsPerRpc;

  /**
   * Cannot be directly instantiated, use {@link Builder} instead.
   */
  private DatastoreServiceConfig() {}

  /**
   * Copy constructor
   */
  DatastoreServiceConfig(DatastoreServiceConfig config) {
    implicitTransactionManagementPolicy = config.implicitTransactionManagementPolicy;
    readPolicy = config.readPolicy;
    deadline = config.deadline;
    maxRpcSizeBytes = config.maxRpcSizeBytes;
    maxBatchWriteEntities = config.maxBatchWriteEntities;
    maxBatchReadEntities = config.maxBatchReadEntities;
    maxEntityGroupsPerRpc = config.maxEntityGroupsPerRpc;
  }

  /**
   * Sets the implicit transaction management policy.
   * @param p the implicit transaction management policy to set.
   * @return {@code this} (for chaining)
   */
  public DatastoreServiceConfig implicitTransactionManagementPolicy(
      ImplicitTransactionManagementPolicy p) {
    if (p == null) {
      throw new NullPointerException("implicit transaction management policy must not be null");
    }
    implicitTransactionManagementPolicy = p;
    return this;
  }

  /**
   * Sets the read policy.
   * @param readPolicy the read policy to set.
   * @return {@code this} (for chaining)
   */
  public DatastoreServiceConfig readPolicy(ReadPolicy readPolicy) {
    if (readPolicy == null) {
      throw new NullPointerException("read policy must not be null");
    }
    this.readPolicy = readPolicy;
    return this;
  }

  /**
   * Sets the deadline, in seconds, for all rpcs initiated by the
   * {@link DatastoreService} with which this config is associated.
   * @param deadline the deadline to set.
   * @throws IllegalArgumentException if deadline is not positive
   * @return {@code this} (for chaining)
   */
  public DatastoreServiceConfig deadline(double deadline) {
    if (deadline <= 0.0) {
      throw new IllegalArgumentException("deadline must be > 0, got " + deadline);
    }
    this.deadline = deadline;
    return this;
  }

  /**
   * Sets the maximum number of entities that can be modified in a single RPC.
   * @param maxBatchWriteEntities the limit to set
   * @throws IllegalArgumentException if maxBatchWriteEntities is not greater
   * than zero
   * @return {@code this} (for chaining)
   */
  DatastoreServiceConfig maxBatchWriteEntities(int maxBatchWriteEntities) {
    if (maxBatchWriteEntities <= 0) {
      throw new IllegalArgumentException("maxBatchWriteEntities must be > 0, got "
          + maxBatchWriteEntities);
    }
    this.maxBatchWriteEntities = maxBatchWriteEntities;
    return this;
  }

  /**
   * Sets the maximum number of entities that can be read in a single RPC.
   * @param maxBatchReadEntities the limit to set
   * @throws IllegalArgumentException if maxBatchReadEntities is not greater
   * than zero
   * @return {@code this} (for chaining)
   */
  DatastoreServiceConfig maxBatchReadEntities(int maxBatchReadEntities) {
    if (maxBatchReadEntities <= 0) {
      throw new IllegalArgumentException("maxBatchReadEntities must be > 0, got "
          + maxBatchReadEntities);
    }
    this.maxBatchReadEntities = maxBatchReadEntities;
    return this;
  }

  /**
   * Sets the maximum size in bytes an RPC can be.
   *
   * The size of the request can be exceeded if the RPC cannot be split enough
   * to respect this limit. However there may be a hard limit on the RPC which,
   * if exceeded, will cause an exception to be thrown.
   *
   * @param maxRpcSizeBytes the limit to set
   * @throws IllegalArgumentException if maxRpcSizeBytes is not positive
   * @return {@code this} (for chaining)
   */
  DatastoreServiceConfig maxRpcSizeBytes(int maxRpcSizeBytes) {
    if (maxRpcSizeBytes < 0) {
      throw new IllegalArgumentException("maxRpcSizeBytes must be >= 0, got "
          + maxRpcSizeBytes);
    }
    this.maxRpcSizeBytes = maxRpcSizeBytes;
    return this;
  }

  /**
   * Sets the maximum number of entity groups that can be represented in a
   * single rpc.
   *
   * For a non-transactional operation that involves more entity groups than the
   * maximum, the operation will be performed by executing multiple, asynchronous
   * rpcs to the datastore, each of which has no more entity groups represented
   * than the maximum.  So, if a put() operation has 8 entity groups and the
   * maximum is 3, we will send 3 rpcs, 2 with 3 entity groups and 1 with 2
   * entity groups.  This is a performance optimization - in many cases
   * multiple, small, asynchronous rpcs will finish faster than a single large
   * asynchronous rpc.  The optimal value for this property will be
   * application-specific, so experimentation is encouraged.
   *
   * @param maxEntityGroupsPerRpc the maximum number of entity groups per rpc
   * @throws IllegalArgumentException if maxEntityGroupsPerRpc is not greater
   * than zero
   * @return {@code this} (for chaining)
   */
  public DatastoreServiceConfig maxEntityGroupsPerRpc(int maxEntityGroupsPerRpc) {
    if (maxEntityGroupsPerRpc <= 0) {
      throw new IllegalArgumentException("maxEntityGroupsPerRpc must be > 0, got "
          + maxEntityGroupsPerRpc);
    }
    this.maxEntityGroupsPerRpc = maxEntityGroupsPerRpc;
    return this;
  }

  /**
   * @return The {@code ImplicitTransactionManagementPolicy} to use.
   */
  public ImplicitTransactionManagementPolicy getImplicitTransactionManagementPolicy() {
    return implicitTransactionManagementPolicy;
  }

  /**
   * @return The {@code ReadPolicy} to use.
   */
  public ReadPolicy getReadPolicy() {
    return readPolicy;
  }

  /**
   * @return The maximum number of entity groups per rpc.  Can be {@code null}.
   */
  public Integer getMaxEntityGroupsPerRpc() {
    return maxEntityGroupsPerRpc;
  }

  /**
   * @return The deadline to use.  Can be {@code null}.
   */
  public Double getDeadline() {
    return deadline;
  }

  boolean exceedsWriteLimits(int count, int size) {
    return (count > maxBatchWriteEntities ||
        (count > 1 && size > maxRpcSizeBytes));
  }

  boolean exceedsReadLimits(int count, int size) {
    return (count > maxBatchReadEntities ||
        (count > 1 && size > maxRpcSizeBytes));
  }

  /**
   * Contains static creation methods for {@link DatastoreServiceConfig}.
   */
  public static final class Builder {

    /**
     * Create a {@link DatastoreServiceConfig} with the given implicit
     * transaction management policy.
     * @param p the implicit transaction management policy to set.
     * @return The newly created DatastoreServiceConfig instance.
     */
    public static DatastoreServiceConfig withImplicitTransactionManagementPolicy(
        ImplicitTransactionManagementPolicy p) {
      return withDefaults().implicitTransactionManagementPolicy(p);
    }

    /**
     * Create a {@link DatastoreServiceConfig} with the given read
     * policy.
     * @param readPolicy the read policy to set.
     * @return The newly created DatastoreServiceConfig instance.
     */
    public static DatastoreServiceConfig withReadPolicy(ReadPolicy readPolicy) {
      return withDefaults().readPolicy(readPolicy);
    }

    /**
     * Create a {@link DatastoreServiceConfig} with the given deadline
     * @param deadline the deadline to set.
     * @return The newly created DatastoreServiceConfig instance.
     */
    public static DatastoreServiceConfig withDeadline(double deadline) {
      return withDefaults().deadline(deadline);
    }

    /**
     * Create a {@link DatastoreServiceConfig} with the given maximum entity
     * groups per rpc.
     * @param maxEntityGroupsPerRpc the maximum entity groups per rpc to set.
     * @return The newly created DatastoreServiceConfig instance.
     *
     * @see {@link DatastoreServiceConfig#maxEntityGroupsPerRpc(int)}
     */
    public static DatastoreServiceConfig withMaxEntityGroupsPerRpc(int maxEntityGroupsPerRpc) {
      return withDefaults().maxEntityGroupsPerRpc(maxEntityGroupsPerRpc);
    }
    /**
     * Helper method for creating a {@link DatastoreServiceConfig}
     * instance with default values: Implicit transactions are disabled, reads
     * execute with {@link Consistency#STRONG}, and no deadline is
     * provided.  When no deadline is provided, datastore rpcs execute with the
     * system-defined deadline.
     *
     * @return The newly created DatastoreServiceConfig instance.
     */
    public static DatastoreServiceConfig withDefaults() {
      return new DatastoreServiceConfig();
    }

    private Builder() {}
  }
}
