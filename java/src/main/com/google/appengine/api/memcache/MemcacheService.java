// Copyright 2008 Google Inc.  All rights reserved

package com.google.appengine.api.memcache;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * The Java API for the App Engine Memcache service.  This offers a fast
 * distrubted cache for commonly-used data.  The cache is limited both in
 * duration and also in total space, so objects stored in it may be discarded
 * at any time.
 * <p>
 * Note that {@code null} is a legal value to store in the cache, or to use
 * as a cache key.  Although the API is written for {@link Object}s, both
 * keys and values should be {@link Serializable}, although future versions
 * may someday accept specific types of non-{@code Serializable} {@code Objects}.
 * <p>
 * The values returned from this API are mutable copies from the cache; altering
 * them has no effect upon the cached value itself until assigned with one of
 * the {@link #put(Object, Object) put} methods.  Likewise, the methods returning
 * collections return mutable collections, but changes do not affect the cache.
 * <p>
 * Methods that operate on single entries, including {@link #increment}, are atomic,
 * while batch methods such as {@link #getAll}, {@link #putAll}, and {@link #deleteAll}
 * do not provide atomicity.  Arbitrary operations on single entries can be performed
 * atomically by using {@link #putIfUntouched} in combination with {@link #getIdentifiable}.
 *
 * <p>
 * {@link #increment Increment} has a number of caveats to its use; please
 * consult the method documentation.
 *
 */
public interface MemcacheService {

  /**
   * Cache replacement strategies for {@link MemcacheService#put} operations,
   * indicating how to handle putting a value that already exists.
   */
  enum SetPolicy {
    /**
     * Always stores the new value.  If an existing value was stored with the
     * given key, it will be discarded and replaced.
     */
    SET_ALWAYS,

    /**
     * An additive-only strategy, useful to avoid race conditions.
     */
    ADD_ONLY_IF_NOT_PRESENT,

    /**
     * A replace-only strategy.
     */
    REPLACE_ONLY_IF_PRESENT
  }

  /**
   * Encapsulates an Object that is returned by {@link #getIdentifiable}.
   * An {@code IdentifiableValue} can later be used in a {@link #putIfUntouched}
   * operation.
   */
  interface IdentifiableValue {
    /**
     * @return the encapsulated value object.
     */
    Object getValue();
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
  String getNamespace();

  /**
   * @deprecated use {@link
   * com.google.appengine.api.memcache.MemcacheServiceFactory#getMemcacheService(String)}
   * instead.
   */
  @Deprecated
  void setNamespace(String newNamespace);

  /**
   * Fetches a previously-stored value, or {@code null} if unset.  Since
   * {@code null} might be the set value in some cases, so we also have
   * {@link MemcacheService#contains(Object)} which returns {@code boolean}.
   *
   * @param key the key object used to store the cache entry
   * @return the value object previously stored, or {@code null}
   * @throws IllegalArgumentException if the type of the key cannot be
   *    supported.
   * @throws InvalidValueException for any error in reconstituting the cache
   *    value.
   */
  Object get(Object key);

  /**
   * Similar to {@link #get}, but returns an object that can later be used
   * to perform a {@link #putIfUntouched} operation.
   *
   * @param key the key object used to store the cache entry
   * @return an {@link #IdentifiableValue} object that wraps the
   * value object previously stored.  {@code null} is returned if {@code key}
   * is not present in the cache.
   */
  public IdentifiableValue getIdentifiable(Object key);

  /**
   * Tests whether a given value is in cache, even if its value is {@code null}.
   * <p>
   * Note that, because an object may be removed from cache at any time, the
   * following is not sound code:
   * <pre>
   *   if (memcache.contains("key")) {
   *     foo = memcache.get("key");
   *     if (foo == null) {
   *       // continue, assuming foo had the real value null
   *     }
   *   }
   *  </pre>
   *  The problem is that the cache could have dropped the entry between the
   *  call to {@code contains} and @{link {@link #get(Object) get}.  This is
   *  a sounder pattern:
   *  <pre>
   *   foo = memcache.get("key");
   *   if (foo == null) {
   *     if (memcache.contains("key")) {
   *       // continue, assuming foo had the real value null
   *     } else {
   *       // continue; foo may have had a real null, but has been dropped now
   *     }
   *   }
   *  </pre>
   *  Another alternative is to prefer {@link #getAll(Collection)}, although
   *  it requires making an otherwise-unneeded {@code Collection} of some sort.
   *
   * @param key the key object used to store the cache entry
   * @return {@code true} if the cache contains an entry for the key
   * @throws IllegalArgumentException if the key cannot be serialized
   */
  boolean contains(Object key);

  /**
   * Performs a get of multiple keys at once.  This is more efficient than
   * multiple separate calls to {@link #get(Object)}, and allows a single
   * call to both test for {@link #contains(Object)} and also fetch the value,
   * because the return will not include mappings for keys not found.
   *
   * @param keys a collection of keys for which values should be retrieved
   * @return a mapping from keys to values of any entries found.  If a requested
   *     key is not found in the cache, the key will not be in the returned Map.
   * @throws IllegalArgumentException if an element of {@code keys} cannot be
   *    used as a cache key.  They should be {@link Serializable}.
   * @throws InvalidValueException for any error in deserializing the cache
   *    value.
   */
  <T> Map<T, Object> getAll(Collection<T> keys);

  /**
   * Store a new value into the cache, using {@code key}, but subject to the
   * {@code policy} regarding existing entries.
   *
   * @param key the key for the new cache entry
   * @param value the value to be stored
   * @param expires an {@link Expiration} object to set time-based expiration.
   *    {@code null} may be used indicate no specific expiration.
   * @param policy Requests particular handling regarding pre-existing entries
   *    under the same key.  This parameter must not be {@code null}.
   * @return {@code true} if a new entry was created, {@code false} if not
   *    because of the {@code policy}.
   * @throws IllegalArgumentException if the key or value type can't
   *    be stored as a cache item.  They should be {@link Serializable}.
   */
  boolean put(Object key, Object value, Expiration expires,
      SetPolicy policy);

  /**
   * Convenience put, equivalent to {@link #put(Object,Object,Expiration,SetPolicy)
   * put(key, value, expiration, SetPolicy.SET_ALWAYS)}.
   *
   * @param key key of the new entry
   * @param value value for the new entry
   * @param expires time-based {@link Expiration}, or {@code null} for none
   * @throws IllegalArgumentException if the key or value type can't
   *    be stored as a cache item.  They should be {@link Serializable}.
   */
  void put(Object key, Object value, Expiration expires);

  /**
   * A convenience shortcut, equivalent to {@link #put(Object,Object,Expiration,SetPolicy)
   * put(key, value, null, SetPolicy.SET_ALWAYS)}.
   *
   * @param key key of the new entry
   * @param value value for the new entry
   * @throws IllegalArgumentException if the key or value type can't
   *    be stored as a cache item.  They should be {@link Serializable}.
   */
  void put(Object key, Object value);

  /**
   * A batch-processing variant of {@link #put}.  This is more efficiently
   * implemented by the service than multiple calls.
   *
   * @param values the key/value mappings to add to the cache
   * @param expires the expiration time for all {@code values}, or
   *    {@code null} for no time-based expiration.
   * @param policy what to do if the entry is or is not already present
   * @return the set of keys for which entries were created.  Keys in
   *    {@code values} may not be in the returned set because of the
   *    {@code policy} regarding pre-existing entries.
   * @throws IllegalArgumentException if the key or value type can't
   *    be stored as a cache item.  They should be {@link Serializable}.
   */
  <T> Set<T> putAll(Map<T, ?> values, Expiration expires,
      SetPolicy policy);

  /**
   * Atomically, store {@code newValue} only if no other value has been stored
   * since {@code oldValue} was retreived. {@code oldValue} is an
   * {@link #IdentifiableValue} that was returned from a previous call to
   * {@link #getIdentifiable}.
   * <p>
   * If another value in the cache for {@code key} has been stored, or if
   * this cache entry has been evicted, then nothing is stored by this call and
   * {@code false} is returned.
   * <p>
   * Note that storing the same value again <i>does</i> count as a "touch" for this
   * purpose.
   * <p>
   * Using {@link #getIdentifiable} and {@link #putIfUntouched} together constitutes
   * an operation that either succeeds atomically or fails due to concurrency
   * (or eviction), in which case the entire operation can be retried by the application.
   *
   * @param key key of the entry
   * @param oldValue identifier for the value to compare against newValue
   * @param newValue new value to store if oldValue is still there
   * @param expires an {@link Expiration} objct to set time-based expiration.
   *    {@code null} may be used to indicate no specific expiration.
   * @return {@code true} if {@code newValue} was stored, {@code false} otherwise.
   * @throws IllegalArgumentException if the key or value type can't be stored.
   *    They should be {@link Serializable}.  Also throws IllegalArgumentException
   *    if oldValue is null.
   */
  boolean putIfUntouched(Object key, IdentifiableValue oldValue,
                         Object newValue, Expiration expires);

  /**
   * Convenience shortcut, equivalent to {@link
   * #putIfUntouched(Object,IdentifiableValue,Object,Expiration) put(key, oldValue, newValue,
   * null)}.
   *
   * @param key key of the entry
   * @param oldValue identifier for the value to compare against newValue
   * @param newValue new value to store if oldValue is still there
   * @return {@code true} if {@code newValue} was stored, {@code false} otherwise.
   * @throws IllegalArgumentException if the key or value type can't be stored.
   *    They should be {@link Serializable}.  Also throws IllegalArgumentException
   *    if oldValue is null.
   */
  boolean putIfUntouched(Object key, IdentifiableValue oldValue, Object newValue);

  /**
   * Convenience multi-put, equivalent to {@link #putAll(Map, Expiration, SetPolicy)
   * putAll(values, expires, SetPolicy.SET_ALWAYS)}.
   *
   * @param values key/value mappings to add to the cache
   * @param expires expiration time for the new values, or {@code null} for no
   *    time-based expiration
   * @throws IllegalArgumentException if the key or value type can't
   *    be stored as a cache item.  They should be {@link Serializable}.
   */
  void putAll(Map<?, ?> values, Expiration expires);

  /**
   * Convenience multi-put, equivalent to {@link #putAll(Map, Expiration, SetPolicy)
   * putAll(values, expires, SetPolicy.SET_ALWAYS)}.
   *
   * @param values key/value mappings for new entries to add to the cache
   * @throws IllegalArgumentException if the key or value type can't
   *    be stored as a cache item.  They should be {@link Serializable}.
   */
  void putAll(Map<?, ?> values);

  /**
   * Removes {@code key} from the cache.
   *
   * @param key the key of the entry to delete.
   * @return {@code true} if an entry existed, but was discarded
   * @throws IllegalArgumentException if the key can't be used in the cache
   *    because it is not {@link Serializable}.
   */
  boolean delete(Object key);

  /**
   * Removes the given key from the cache, and prevents it from being added
   * under the {@link SetPolicy#ADD_ONLY_IF_NOT_PRESENT} policy for {@code millisNoReAdd}
   * milliseconds thereafter.  Calls to a {@link #put} method using
   * {@link SetPolicy#SET_ALWAYS} are not blocked, however.
   *
   * @param key key to delete
   * @param millisNoReAdd time during which calls to put using ADD_IF_NOT_PRESENT
   *     should be denied.
   * @return {@code true} if an entry existed to delete
   * @throws IllegalArgumentException if the key can't be used in the cache
   *    because it is not {@link Serializable}.
   */
  boolean delete(Object key, long millisNoReAdd);

  /**
   * Batch version of {@link #delete(Object)}.
   *
   * @param keys a collection of keys for entries to delete
   * @return the Set of keys deleted.  Any keys in {@code keys} but not in the
   *    returned set were not found in the cache.
   * @throws IllegalArgumentException if a key can't be used in the cache
   *    because it is not {@link Serializable}.
   */
  <T> Set<T> deleteAll(Collection<T> keys);

  /**
   * Batch version of {@link #delete(Object, long)}.
   *
   * @param keys the keys to be deleted
   * @param millisNoReAdd time during which calls to put using
   *    {@link SetPolicy#ADD_ONLY_IF_NOT_PRESENT} should be denied.
   * @return the set of keys deleted.
   * @throws IllegalArgumentException if the key can't be used in the cache
   *    because it is not {@link Serializable}.
   */
  <T> Set<T> deleteAll(Collection<T> keys, long millisNoReAdd);

  /**
   * Atomically fetches, increments, and stores a given integral value.
   * "Integral" types are {@link Byte}, {@link Short}, {@link Integer},
   * {@link Long}, and in some cases {@link String} (if the string is parseable
   * as a number, for example via {@link Long#parseLong(String)}. The entry must
   * already exist, and have a non-negative value.
   * <p>
   * Incrementing by positive amounts will reach signed 64-bit max (
   * {@code 2^63 - 1}) and then wrap-around to signed 64-bit min ({@code -2^63}
   * ), continuing increments from that point.
   * <p>
   * To facilitate use as an atomic countdown, incrementing by a negative value
   * (i.e. decrementing) will not go below zero: incrementing {@code 2} by
   * {@code -5} will return {@code 0}, not {@code -3}. However, due to the way
   * numbers are stored, decrementing {@code -3} by {@code -5} will result in
   * {@code -8}; so the zero-floor rule only applies to decrementing numbers
   * that were positive.
   * <p>
   * Note: The actual representation of all numbers in Memcache is a string.
   * This means if you initially stored a number as a string (e.g., "10") and
   * then increment it, everything will work properly, including wrapping beyond
   * signed 64-bit int max. However, if you {@link #get(Object)} the key past
   * the point of wrapping, you will receive an unsigned integer value,
   * <em>not</em> a signed integer value.
   *
   * @param key the key of the entry to manipulate
   * @param delta the size of the increment, positive or negative.
   * @return the post-increment value, as a long. However, a
   *         {@link #get(Object)} of the key will still have the original type (
   *         {@link Byte}, {@link Short}, etc.). If there is no entry for
   *         {@code key}, returns {@code null}.
   * @throws IllegalArgumentException if the key can't be used in the cache
   *         because it is not {@link Serializable}.
   * @throws InvalidValueException if the object incremented is not of a
   *         integral type
   */
  Long increment(Object key, long delta);

  /**
   * Like normal increment, but allows for an optional initial value for the
   * key to take on if not already present in the cache.
   *
   * @param initialValue value to insert into the cache if the key is not
   *   present
   */
  Long increment(Object key, long delta, Long initialValue);

  /**
   * Like normal increment, but increments a batch of separate keys in
   * parallel by the same delta.
   *
   * @return mapping keys to their new values; values will be null if they could
   *   not be incremented or were not present in the cache
   */
  <T> Map<T, Long> incrementAll(Collection<T> keys, long delta);

  /**
   * Like normal increment, but increments a batch of separate keys in
   * parallel by the same delta and potentially sets a starting value.
   *
   * @param initialValue value to insert into the cache if the key is not
   *   present
   * @return mapping keys to their new values; values will be null if they could
   *   not be incremented for whatever reason
   */
  <T> Map<T, Long> incrementAll(Collection<T> keys, long delta, Long initialValue);

  /**
   * Like normal increment, but accepts a mapping of separate controllable
   * offsets for each key individually. Good for incrementing by a sum and
   * a count in parallel.
   *
   * @return mapping keys to their new values; values will be null if they could
   *   not be incremented for whatever reason
   */
  <T> Map<T, Long> incrementAll(Map<T, Long> offsets);

  /**
   * Like normal increment, but accepts a mapping of separate controllable
   * offsets for each key individually. Good for incrementing by a sum and
   * a count in parallel. Callers may also pass an initial value for the keys
   * to take on if they are not already present in the cache.
   *
   * @return mapping keys to their new values; values will be null if they could
   *   not be incremented for whatever reason
   */
  <T> Map<T, Long> incrementAll(Map<T, Long> offsets, Long initialValue);

  /**
   * Empties the cache of all values.  Statistics are not
   * affected. Note that {@code clearAll()} does not respect
   * namespaces - this flushes the cache for every namespace.
   */
  void clearAll();

  /**
   * Fetches some statistics about the cache and its usage.
   *
   * @return statistics for the cache. Note that {@code getStatistics()} does
   * not respect namespaces - this will return stats for every namespace.  The
   * response will never be {@code null}.
   */
  Stats getStatistics();

  /**
   * Registers a new {@code ErrorHandler}.  The {@code handler} is used to field
   * any errors which aren't necessarily the application programmer's fault:
   * things like network timeouts, for example.  Value correctness and usage
   * errors {@link IllegalArgumentException}, {@link InvalidValueException} are
   * still thrown normally.
   *
   * @param handler the new {@code ErrorHandler} to use.  May not be
   * {@code null}.
   */
  void setErrorHandler(ErrorHandler handler);

  /**
   * Fetches the current error handler.
   */
  ErrorHandler getErrorHandler();
}
