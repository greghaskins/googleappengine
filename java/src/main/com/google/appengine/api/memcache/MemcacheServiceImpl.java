// Copyright 2008 Google Inc.  All rights reserved

package com.google.appengine.api.memcache;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.memcache.MemcacheService.IdentifiableValue;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheBatchIncrementRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheBatchIncrementResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheDeleteRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheDeleteResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheDeleteResponse.DeleteStatusCode;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheFlushRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheFlushResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGrabTailRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGrabTailResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementRequest.Direction;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementResponse.IncrementStatusCode;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse.SetStatusCode;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheStatsRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheStatsResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MergedNamespaceStats;
import com.google.apphosting.api.ApiProxy;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Java bindings for the Memcache service.
 *
 */
class MemcacheServiceImpl implements MemcacheService {

  static final String PACKAGE = "memcache";

  private static final Logger logger = Logger.getLogger(MemcacheServiceImpl.class.getName());

  /**
   * Default handler just logs errors at INFO.
   */
  private ErrorHandler handler = new LogAndContinueErrorHandler(Level.INFO);

  /**
   * If the namespace is not null it overrides current namespace on all API
   * calls. The current namespace is defined as the one returned by
   * {@link NamespaceManager#get()}.
   */
  private String namespace;

  /**
   * Our keys will be byte[], which by default doesn't do hashCode() and
   * equals() correctly.  This wraps it to do so, using {@link Arrays}.
   * For most methods we don't care, but a few (the multi-puts and delete)
   * need to map back from the "actual" downstream key to the originating
   * Object key.
   */
  private class CacheKey {
    private byte[] keyval;
    private int hashcode;

    public CacheKey(byte[] bytes) {
      keyval = bytes;
      hashcode = Arrays.hashCode(keyval);
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof CacheKey) {
        return Arrays.equals(keyval, ((CacheKey) other).keyval);
      } else {
        return false;
     }
    }

    @Override
    public int hashCode() {
      return hashcode;
    }
  }

  private class StatsImpl implements Stats {
    private long hits, misses, bytesFetched, items, bytesStored;
    private int maxCachedTime;

    private StatsImpl(long hits, long misses, long bytesFetched, long items,
                      long bytesStored, int maxCachedTime) {
      this.hits = hits;
      this.misses = misses;
      this.bytesFetched = bytesFetched;
      this.items = items;
      this.bytesStored = bytesStored;
      this.maxCachedTime = maxCachedTime;
    }

    @Override
    public long getHitCount() {
      return hits;
    }

    @Override
    public long getMissCount() {
      return misses;
    }

    @Override
    public long getBytesReturnedForHits() {
      return bytesFetched;
    }

    @Override
    public long getItemCount() {
      return items;
    }

    @Override
    public long getTotalItemBytes() {
      return bytesStored;
    }

    @Override
    public int getMaxTimeWithoutAccess() {
      return maxCachedTime;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("Hits: " + hits + "\n");
      builder.append("Misses: " + misses + "\n");
      builder.append("Bytes Fetched: " + bytesFetched + "\n");
      builder.append("Bytes Stored: " + bytesStored + "\n");
      builder.append("Items: " + items + "\n");
      builder.append("Max Cached Time: " + maxCachedTime + "\n");
      return builder.toString();
    }
  }

  protected static class IdentifiableValueImpl implements IdentifiableValue {
    private Object value;
    private long casId;

    protected IdentifiableValueImpl(Object value, long casId) {
      this.value = value;
      this.casId = casId;
    }

    public Object getValue() {
      return value;
    }

    protected long getCasId() {
      return casId;
    }
  }

  MemcacheServiceImpl(String namespace) {
    if (namespace != null) {
      NamespaceManager.validateNamespace(namespace);
    }
    this.namespace = namespace;
  }

  /**
   * Issue an rpc against the memcache package with the given request and
   * response pbs as input and apply standard exception handling.  Do not
   * use this helper function if you need non-standard exception handling.
   *
   * @return {@code true} if the RPC returned without throwing an exception,
   * {@code false} otherwise.
   */
  private boolean makeSyncCall(String methodName, Message request,
      Message.Builder response, String errorText) {
    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, methodName, request.toByteArray());
      response.mergeFrom(responseBytes);
      return true;
    } catch (InvalidProtocolBufferException ex) {
      handler.handleServiceError(new MemcacheServiceException("Could not decode response:", ex));
    } catch (ApiProxy.ApplicationException ae) {
      logger.info(errorText + ": " + ae.getErrorDetail());
      handler.handleServiceError(new MemcacheServiceException(errorText));
    } catch (ApiProxy.ApiProxyException ex) {
      handler.handleServiceError(new MemcacheServiceException(errorText, ex));
    }
    return false;
  }

  /**
   * Method returns non-null value if the MemcacheService overrides the
   * default namespace in API calls. Default namespace is the one returned
   * by {@link com.google.appengine.api.NamespaceManager#get()}.
   *
   * @return {@code null} if the MemcacheService uses default namespace in
   * API calls. Otherwise it returns {@code namespace} which is overrides
   * default namespace on the API calls.
   */
  public String getNamespace() {
    return namespace;
  }

  /**
   * @deprecated use {@link
   * com.google.appengine.api.memcache.MemcacheServiceFactory#getMemcacheService(String)}
   * instead.
   */
  @Deprecated
  public void setNamespace(String newNamespace) {
    namespace = newNamespace;
  }

  /**
   * Returns namespace which is about to be used by API call. By default it is
   * the value returned by {@link NamespaceManager#get()} with the exception
   * that {@code null} is substituted with "" (empty string).
   * If the {@link #namespace} is not null it overrides the default value.
   */
  private String getEffectiveNamespace() {
    if (namespace != null) {
      return namespace;
    }
    String namespace1 = NamespaceManager.get();
    return namespace1 == null ? "" : namespace1;
  }

  /**
   * Tests whether a given value is in cache, per {@link
   * MemcacheService#contains(Object)}.
   */
  public boolean contains(Object key) {
    MemcacheGetResponse.Builder response = MemcacheGetResponse.newBuilder();

    MemcacheGetRequest request;
    try {
      request = MemcacheGetRequest.newBuilder()
          .setNameSpace(getEffectiveNamespace())
          .addKey(ByteString.copyFrom(MemcacheSerialization.makePbKey(key)))
          .build();
    } catch (IOException ex) {
      throw new IllegalArgumentException("Cannot use as key: '" + key + "'", ex);
    }
    if (!makeSyncCall("Get", request, response,
        "Memcache contains: exception testing contains (" + key + ")")) {
      return false;
    }
    return (response.getItemCount() == 1);
  }

  private Object doGet(Object key, boolean forCas) {
    MemcacheGetResponse.Builder response = MemcacheGetResponse.newBuilder();

    MemcacheGetRequest request;
    try {
      MemcacheGetRequest.Builder requestBuilder = MemcacheGetRequest.newBuilder();
      requestBuilder.setNameSpace(getEffectiveNamespace())
          .addKey(ByteString.copyFrom(MemcacheSerialization.makePbKey(key)));
      if (forCas) {
        requestBuilder.setForCas(true);
      }
      request = requestBuilder.build();
    } catch (IOException ex) {
      throw new IllegalArgumentException("Cannot use as a key: '" + key + "'", ex);
    }
    if (!makeSyncCall("Get", request, response,
        "Memcache get: exception getting 1 key (" + key + ")")) {
      return null;
    }
    if (response.getItemCount() == 0) {
      return null;
    }
    MemcacheGetResponse.Item item = response.getItem(0);
    Object value;

    try {
      value = MemcacheSerialization.deserialize(item.getValue().toByteArray(), item.getFlags());
    } catch (ClassNotFoundException ex) {
      handler.handleDeserializationError(new InvalidValueException(
          "Can't find class for value of key '" + key + "'", ex));
      return null;
    } catch (IOException ex) {
      throw new InvalidValueException("IO exception parsing value of '" + key + "'", ex);
    }
    if (forCas) {
      return new IdentifiableValueImpl(value, item.getCasId());
    } else {
      return value;
    }
  }

  /**
   * Fetches a previously-stored value, per {@link MemcacheService#get(Object)}.
   *
   * @param key the key object used to store the cache entry
   * @return the value object stored, or <code>null</code>
   */
  public Object get(Object key) {
    return doGet(key, false);
  }

  /**
   * Gets a value that can later be used with putIfUntouched().
   */
  public IdentifiableValue getIdentifiable(Object key) {
    return (IdentifiableValue) doGet(key, true);
  }

  /**
   * A bundled multi-get, per {@link MemcacheService#getAll(Collection)}.
   */
  public <T> Map<T, Object> getAll(Collection<T> keys) {
    MemcacheGetResponse.Builder response = MemcacheGetResponse.newBuilder();

    MemcacheGetRequest.Builder requestBuilder = MemcacheGetRequest.newBuilder();
    requestBuilder.setNameSpace(getEffectiveNamespace());

    Map<CacheKey, T> cacheKeyToObjectKey = new HashMap<CacheKey,T>();
    for (T key : keys) {
      try {
        byte keybytes[] = MemcacheSerialization.makePbKey(key);
        cacheKeyToObjectKey.put(new CacheKey(keybytes), key);
        requestBuilder.addKey(ByteString.copyFrom(keybytes));
      } catch (IOException ex) {
        throw new IllegalArgumentException("Cannot use as key: '" + key + "'", ex);
      }
    }
    if (!makeSyncCall("Get", requestBuilder.build(), response,
                      "Memcache get: exception getting multiple keys")) {
      return Collections.<T, Object>emptyMap();
    }

    Map<T, Object> result = new HashMap<T, Object>();
    for (MemcacheGetResponse.Item item : response.getItemList()) {
      T key = null;
      try {
        key = cacheKeyToObjectKey.get(new CacheKey(item.getKey().toByteArray()));
        Object obj = MemcacheSerialization.deserialize(item.getValue().toByteArray(),
             item.getFlags());
        result.put(key, obj);
      } catch (ClassNotFoundException ex) {
        handler.handleDeserializationError(new InvalidValueException(
            "Can't find class for value of key '" + key + "'", ex));
        return null;
      } catch (IOException ex) {
        throw new InvalidValueException("IO exception parsing value of '" + key + "'", ex);
      }
    }
    return result;
  }

  /**
   * Note: non-null oldValue implies Compare-and-Swap operation.
   */
  private boolean doPut(Object key, IdentifiableValue oldValue, Object value,
                       Expiration expires, SetPolicy policy) {

    MemcacheSetResponse.Builder response = MemcacheSetResponse.newBuilder();

    MemcacheSetRequest.Builder requestBuilder = MemcacheSetRequest.newBuilder();
    requestBuilder.setNameSpace(getEffectiveNamespace());

    MemcacheSetRequest.Item.Builder itemBuilder = MemcacheSetRequest.Item.newBuilder();
    try {
      MemcacheSerialization.ValueAndFlags vaf = MemcacheSerialization.serialize(value);
      itemBuilder.setValue(ByteString.copyFrom(vaf.value));
      itemBuilder.setFlags(vaf.flags.ordinal());
    } catch (IOException ex) {
      throw new IllegalArgumentException("Cannot use as value: '" + value + "'", ex);
    }
    try {
      itemBuilder.setKey(ByteString.copyFrom(MemcacheSerialization.makePbKey(key)));
    } catch (IOException ex) {
      throw new IllegalArgumentException("Cannot use as key: '" + key + "'", ex);
    }
    itemBuilder.setExpirationTime(expires == null ? 0 : expires.getSecondsValue());
    if (oldValue == null) {
      itemBuilder.setSetPolicy(convertSetPolicyToPb(policy));
    } else {
      itemBuilder.setSetPolicy(MemcacheSetRequest.SetPolicy.CAS);
      try {
        itemBuilder.setCasId(((IdentifiableValueImpl) oldValue).getCasId());
      } catch (ClassCastException ex) {
        throw new IllegalArgumentException("Not a proper identifiable value: " + oldValue);
      }
      itemBuilder.setForCas(true);
    }

    requestBuilder.addItem(itemBuilder);

    if (!makeSyncCall("Set", requestBuilder.build(), response,
        "Memcache put: exception setting 1 key (" + key + ") to '" + value + "'")) {
      return false;
    }
    if (response.getSetStatusCount() != 1) {
      throw new MemcacheServiceException("Memcache put: Set one item, got "
          + response.getSetStatusCount() + " response statuses");
    }
    SetStatusCode status = response.getSetStatus(0);
    if (status == SetStatusCode.ERROR) {
      throw new MemcacheServiceException("Memcache put: Error setting single item (" + key + ")");
    }
    return (status == SetStatusCode.STORED);
  }

  /**
   * Store a new value into the cache.
   *
   * @param key
   * @param value
   * @param expires null for no time-based expiration, or an Expiration object
   *    otherwise
   * @param policy what to do if the entry is or is not already present
   *
   */
  public boolean put(Object key, Object value, Expiration expires, SetPolicy policy) {
    return doPut(key, null, value, expires, policy);
  }

  private MemcacheSetRequest.SetPolicy convertSetPolicyToPb(SetPolicy policy) {
    switch (policy) {
      case SET_ALWAYS:
        return MemcacheSetRequest.SetPolicy.SET;
      case ADD_ONLY_IF_NOT_PRESENT:
        return MemcacheSetRequest.SetPolicy.ADD;
      case REPLACE_ONLY_IF_PRESENT:
        return MemcacheSetRequest.SetPolicy.REPLACE;
    }
    throw new IllegalArgumentException("Unknown policy: " + policy);
  }

  /**
   * Convenience put, defaulting to SET_ALWAYS for policy.
   */
  public void put(Object key, Object value, Expiration expires) {
    put(key, value, expires, SetPolicy.SET_ALWAYS);
  }

  /**
   * Convenience shortcut, defaulting to SET_ALWAYS for policy and
   * {@code null} for expiration.
   */
  public void put(Object key, Object value) {
    put(key, value, null, SetPolicy.SET_ALWAYS);
  }

  /** See MemcacheService.putIfIntouched. */
  public boolean putIfUntouched(Object key, IdentifiableValue oldValue,
                                 Object newValue, Expiration expires) {
    if (oldValue == null) {
      throw new IllegalArgumentException("oldValue must not be null.");
    }
    if (!(oldValue instanceof IdentifiableValueImpl)) {
      throw new IllegalArgumentException(
          "oldValue is an instance of an unapproved IdentifiableValue implementation.  " +
          "Perhaps you implemented your own version of IdentifiableValue?  " +
          "If so, don't do this.");
    }
    return doPut(key, oldValue, newValue, expires, null);
  }

  /** Convenience putIfUntouched, defaulting to {@code null} for exiration. */
  public boolean putIfUntouched(Object key, IdentifiableValue oldValue, Object newValue) {
    return putIfUntouched(key, oldValue, newValue, null);
  }

  /**
   * Stores multiple new values at once.
   *
   * @param values
   * @param expires null for no time-based expiration, or an Expiration object
   * @param policy what to do if the entry is or is not already present
   * @return set of assigned keys, which may be a subset of the requested
   *    (for example because of policy and existing keys).
   */
  public <T> Set<T> putAll(Map<T, ?> values, Expiration expires,
                            SetPolicy policy) {
    MemcacheSetResponse.Builder response = MemcacheSetResponse.newBuilder();

    MemcacheSetRequest.Builder requestBuilder = MemcacheSetRequest.newBuilder();
    requestBuilder.setNameSpace(getEffectiveNamespace());

    Map<CacheKey, T> cacheKeyToObjectKey = new HashMap<CacheKey, T>();

    for (Map.Entry<T, ?> entry : values.entrySet()) {
      MemcacheSetRequest.Item.Builder itemBuilder = MemcacheSetRequest.Item.newBuilder();
      try {
        byte sha1[] = MemcacheSerialization.makePbKey(entry.getKey());
        cacheKeyToObjectKey.put(new CacheKey(sha1), entry.getKey());
        itemBuilder.setKey(ByteString.copyFrom(sha1));
      } catch (IOException ex) {
        throw new IllegalArgumentException("Cannot use as key: '"
            + entry.getKey() + "'", ex);
      }
      try {
        MemcacheSerialization.ValueAndFlags vaf = MemcacheSerialization.serialize(entry.getValue());
        itemBuilder.setValue(ByteString.copyFrom(vaf.value));
        itemBuilder.setFlags(vaf.flags.ordinal());
      } catch (IOException ex) {
        throw new IllegalArgumentException("Cannot use as value: '"
            + entry.getValue() + "'", ex);
      }
      itemBuilder.setExpirationTime(expires == null ? 0 : expires.getSecondsValue());
      itemBuilder.setSetPolicy(convertSetPolicyToPb(policy));
      requestBuilder.addItem(itemBuilder);
    }
    MemcacheSetRequest request = requestBuilder.build();
    if (!makeSyncCall("Set", request, response,
        "Memcache put: Unknown exception setting " + values.size() + " keys")) {
      return new HashSet<T>();
    }
    HashSet<T> result = new HashSet<T>();
    HashSet<Object> errors = new HashSet<Object>();

    if (response.getSetStatusCount() != values.size()) {
      throw new MemcacheServiceException("Memcache put: Set " + values.size()
                 + " items, got " + response.getSetStatusCount()
                 + " response statuses");
    }
    for (int i = 0; i < values.size(); i++) {
      MemcacheSetResponse.SetStatusCode status = response.getSetStatus(i);
      byte[] key = request.getItem(i).getKey().toByteArray();
      if (status == MemcacheSetResponse.SetStatusCode.ERROR) {
        errors.add(key);
      } else if (status == MemcacheSetResponse.SetStatusCode.STORED) {
        result.add(cacheKeyToObjectKey.get(new CacheKey(key)));
      }
    }
    if (errors.size() != 0) {
      StringBuilder builder = new StringBuilder();
      for (Object err : errors) {
        if (builder.length() > 0) {
          builder.append(", ");
        }
        builder.append(err);
      }
      throw new MemcacheServiceException("Memcache put: Set failed to set "
          + errors.size() + " keys: " + builder.toString());
    }
    return result;
  }

  /**
   * Convenience multi-put, defaulting to SET_ALWAYS for policy.
   */
  public void putAll(Map<?, ?> values, Expiration expires) {
    putAll(values, expires, SetPolicy.SET_ALWAYS);
  }

  /**
   * Convenience multi-put, defaulting to {@code null} for expiration
   * and to SET_ALWAYS for policy.
   */
  public void putAll(Map<?, ?> values) {
    putAll(values, null, SetPolicy.SET_ALWAYS);
  }

  /**
   * Discard key immediately, with no delete hold
   * @param key the key to discard
   * @return {@code true} if an entry existed to delete
   */
  public boolean delete(Object key) {
    return delete(key, 0);
  }

  /**
   * Discard one key, blocking new adds for the given time delay
   * @param key the key to discard
   * @param millisNoReAdd the time to wait before allowing any new addition of
   *      that key.  Takes millis for API consistency, but the back end only
   *      has seconds of resolution.
   * @return {@code true} if we deleted an existing object
   */
  public boolean delete(Object key, long millisNoReAdd){
    MemcacheDeleteResponse.Builder response = MemcacheDeleteResponse.newBuilder();

    MemcacheDeleteRequest request;
    try {
      MemcacheDeleteRequest.Item.Builder item = MemcacheDeleteRequest.Item.newBuilder()
          .setKey(ByteString.copyFrom(MemcacheSerialization.makePbKey(key)))
          .setDeleteTime((int) (millisNoReAdd / 1000));

      request = MemcacheDeleteRequest.newBuilder()
          .setNameSpace(getEffectiveNamespace())
          .addItem(item)
          .build();
    } catch (IOException ex) {
      throw new IllegalArgumentException("Cannot use as key: '" + key + "'", ex);
    }
    if (!makeSyncCall("Delete", request, response,
        "Memcache delete: Unknown exception deleting key: " + key)) {
      return false;
    }
    return response.getDeleteStatus(0) == DeleteStatusCode.DELETED;
  }

  /**
   * Discard all keys immediately, with no delete hold
   * @param keys the keys to discard
   * @return set of deleted keys
   */
  public <T> Set<T> deleteAll(Collection<T> keys) {
    return deleteAll(keys, 0);
  }

  /**
   * Discard many keys, blocking new adds for the given time delay
   * @param keys the keys to discard
   * @param millisNoReAdd the time to wait before allowing any new addition of
   *      that key.  Takes millis for API consistency, but the back end only
   *      has seconds of resolution.
   * @return set of objects which were actually deleted
   */
  public <T> Set<T> deleteAll(Collection<T> keys, long millisNoReAdd) {
    Map<CacheKey, T> cacheKeyToObjectKey = new HashMap<CacheKey, T>();

    MemcacheDeleteRequest.Builder requestBuilder = MemcacheDeleteRequest.newBuilder()
        .setNameSpace(getEffectiveNamespace());

    for (T key : keys) {
      try {
        byte[] sha1 = MemcacheSerialization.makePbKey(key);
        cacheKeyToObjectKey.put(new CacheKey(sha1), key);

        requestBuilder.addItem(MemcacheDeleteRequest.Item.newBuilder()
                               .setDeleteTime((int) (millisNoReAdd / 1000))
                               .setKey(ByteString.copyFrom(sha1)));
      } catch (IOException ex) {
        throw new IllegalArgumentException("Cannot use as key: '" + key + "'", ex);
      }
    }

    MemcacheDeleteRequest request = requestBuilder.build();
    MemcacheDeleteResponse.Builder response = MemcacheDeleteResponse.newBuilder();
    if (!makeSyncCall("Delete", request, response,
        "Memcache delete: Unknown exception deleting multiple keys")) {
      return new HashSet<T>();
    }
    Set<T> retval = new HashSet<T>();
    for (int i = 0; i < response.getDeleteStatusCount(); i++) {
      if (response.getDeleteStatus(i) == DeleteStatusCode.DELETED) {
        retval.add(cacheKeyToObjectKey.get(
                       new CacheKey(request.getItem(i).getKey().toByteArray())));
      }
    }
    return retval;
  }

  private MemcacheIncrementRequest internalBuildIncrementRequest(
      Object key,
      long delta,
      Long initialValue,
      MemcacheIncrementRequest.Builder requestBuilder) {
    try {
      requestBuilder.setKey(
          ByteString.copyFrom(MemcacheSerialization.makePbKey(key)));
    } catch (IOException ex) {
      throw new IllegalArgumentException("Cannot use as key: '" + key + "'", ex);
    }
    if (delta > 0) {
      requestBuilder.setDirection(Direction.INCREMENT);
      requestBuilder.setDelta(delta);
    } else {
      requestBuilder.setDirection(Direction.DECREMENT);
      requestBuilder.setDelta(-delta);
    }
    if (initialValue != null) {
      requestBuilder.setInitialValue(initialValue);
      requestBuilder.setInitialFlags(MemcacheSerialization.Flag.LONG.ordinal());
    }
    return requestBuilder.build();
  }

  /**
   * Atomic increment-and-return-new-value
   * @param key to increment (must be present, must represent int or long)
   * @param delta increment step
   * @return post-increment value bound to key, or null if the key wasn't
   *   present or settable
   */
  public Long increment(Object key, long delta) {
    return increment(key, delta, null);
  }

  /**
   * Atomic increment-and-return-new-value, with an optional initial value.
   * @param key to increment (must be present, must represent int or long)
   * @param delta increment step
   * @param initialValue if not null, the initial value to use if the key is
   *   not already present in memcache
   * @return post-increment value bound to key, or null if the key wasn't
   *   present or settable
   */
  public Long increment(Object key, long delta, Long initialValue) {
    MemcacheIncrementResponse.Builder response = MemcacheIncrementResponse.newBuilder();
    MemcacheIncrementRequest.Builder requestBuilder = MemcacheIncrementRequest.newBuilder();
    MemcacheIncrementRequest request = internalBuildIncrementRequest(
        key, delta, initialValue, requestBuilder.setNameSpace(getEffectiveNamespace()));

    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, "Increment", request.toByteArray());
      response.mergeFrom(responseBytes);
    } catch (InvalidProtocolBufferException ex) {
      handler.handleServiceError(new MemcacheServiceException("Could not decode response:", ex));
    } catch (ApiProxy.ApplicationException ex) {
      logger.info(ex.getErrorDetail());
      throw new InvalidValueException("Non-incrementable value for key '" + key + "'");
    } catch (ApiProxy.ApiProxyException ex) {
      handler.handleServiceError(new MemcacheServiceException(
          "Memcache increment of key '" + key + "': exception", ex));
    }
    if (!response.hasNewValue()) {
      return null;
    }

    return response.getNewValue();
  }

  public <T> Map<T, Long> incrementAll(Collection<T> keys, long delta) {
    return incrementAll(keys, delta, null);
  }

  public <T> Map<T, Long> incrementAll(Collection<T> keys, long delta, Long initialValue) {
    Map<T, Long> offsets = new LinkedHashMap<T, Long>();
    Long deltaLong = Long.valueOf(delta);
    for (T key : keys) {
      offsets.put(key, delta);
    }
    return incrementAll(offsets, initialValue);
  }

  public <T> Map<T, Long> incrementAll(Map<T, Long> offsets) {
    return incrementAll(offsets, null);
  }

  public <T> Map<T, Long> incrementAll(Map<T, Long> offsets, Long initialValue) {
    MemcacheBatchIncrementRequest.Builder requestBuilder =
        MemcacheBatchIncrementRequest.newBuilder().setNameSpace(getEffectiveNamespace());
    MemcacheBatchIncrementResponse.Builder response =
        MemcacheBatchIncrementResponse.newBuilder();

    for (Map.Entry<T, Long> entry : offsets.entrySet()) {
      requestBuilder.addItem(internalBuildIncrementRequest(
          entry.getKey(), entry.getValue(), initialValue,
          MemcacheIncrementRequest.newBuilder()));
    }

    MemcacheBatchIncrementRequest request = requestBuilder.build();
    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(
          PACKAGE, "BatchIncrement", request.toByteArray());
      response.mergeFrom(responseBytes);
    } catch (InvalidProtocolBufferException ex) {
      handler.handleServiceError(new MemcacheServiceException("Could not decode response:", ex));
    } catch (ApiProxy.ApiProxyException ex) {
      handler.handleServiceError(new MemcacheServiceException(
          "Memcache batch increment exception", ex));
    }

    Map<T, Long> result = new HashMap<T, Long>();
    int index = 0;
    for (Map.Entry<T, Long> entry : offsets.entrySet()) {
      if (index < response.getItemCount()) {
        MemcacheIncrementResponse responseItem = response.getItem(index++);
        if (responseItem.getIncrementStatus().equals(IncrementStatusCode.OK) &&
            responseItem.hasNewValue()) {
          result.put(entry.getKey(), responseItem.getNewValue());
        } else {
          result.put(entry.getKey(), null);
        }
      } else {
        result.put(entry.getKey(), null);
      }
    }

    return result;
  }

  public void clearAll() {
    MemcacheFlushRequest request = MemcacheFlushRequest.newBuilder().build();
    MemcacheFlushResponse.Builder response = MemcacheFlushResponse.newBuilder();

    makeSyncCall("FlushAll", request, response, "Memcache flush: exception");
  }

  public Stats getStatistics() {
    MemcacheStatsRequest request = MemcacheStatsRequest.newBuilder().build();
    MemcacheStatsResponse.Builder response = MemcacheStatsResponse.newBuilder();

    if (!makeSyncCall("Stats", request, response,
        "Memcache getStatistics: exception")) {
      return null;
    }
    MergedNamespaceStats stats = response.getStats();
    if (stats == null) {
      return new StatsImpl(0, 0, 0, 0, 0, 0);
    } else {
      return new StatsImpl(stats.getHits(), stats.getMisses(), stats.getByteHits(),
                           stats.getItems(), stats.getBytes(), stats.getOldestItemAge());
    }
  }

  public ErrorHandler getErrorHandler() {
    return handler;
  }

  public void setErrorHandler(ErrorHandler newHandler) {
    handler = newHandler;
  }

  public List<Object> grabTail(int itemCount) {
    if (getEffectiveNamespace().length() == 0) {
      throw new IllegalStateException("Namespace should be non-empty.");
    }

    MemcacheGrabTailResponse.Builder response = MemcacheGrabTailResponse.newBuilder();

    MemcacheGrabTailRequest.Builder requestBuilder = MemcacheGrabTailRequest.newBuilder();
    requestBuilder.setNameSpace(getEffectiveNamespace());
    requestBuilder.setItemCount(itemCount);

    if (!makeSyncCall("GrabTail", requestBuilder.build(), response,
                      "Memcache get: exception getting multiple keys")) {
      return Collections.<Object>emptyList();
    }

    List<Object> result = new ArrayList<Object>();
    for (MemcacheGrabTailResponse.Item item : response.getItemList()) {
      try {
        Object obj = MemcacheSerialization.deserialize(
            item.getValue().toByteArray(), item.getFlags());
        result.add(obj);
      } catch (ClassNotFoundException ex) {
        handler.handleDeserializationError(new InvalidValueException(
            "Can't find class", ex));
        return null;
      } catch (IOException ex) {
        throw new InvalidValueException("IO exception parsing value", ex);
      }
    }
    return result;
  }
}
