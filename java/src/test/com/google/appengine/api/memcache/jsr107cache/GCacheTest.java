// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.api.memcache.jsr107cache;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.Stats;
import com.google.appengine.api.memcache.Expiration;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheStatistics;

/**
 */
public class GCacheTest extends TestCase {
  private static final String key = "a key";
  private static final String value = "a value";
  private static final String key2 = "another key";
  private static final String value2 = "another value";

  private MemcacheService service;
  private Cache cache;
  private Stats stats;
  private IMocksControl control;
  private Map properties;

  @Override
  @SuppressWarnings("unchecked")
  protected void setUp() throws Exception {
    super.setUp();
    control = EasyMock.createStrictControl();
    service = control.createMock(MemcacheService.class);
    stats = control.createMock(Stats.class);
    properties = new HashMap();
    properties.put(GCacheFactory.MEMCACHE_SERVICE, service);
    cache = new GCache(properties);
  }

  /**
   * Tests that a put operation passes to the MemcacheService correctly.
   */
  @SuppressWarnings("unchecked")
  public void testPut() throws Exception {
    service.put(key, value, null, MemcacheService.SetPolicy.SET_ALWAYS);
    EasyMock.expectLastCall().andReturn(true);
    service.put(key, value, null, MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    EasyMock.expectLastCall().andReturn(true);
    service.put(key, value, null, MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT);
    EasyMock.expectLastCall().andReturn(false);
    service.put(key2, value, null, MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    EasyMock.expectLastCall().andReturn(false);
    service.put(key, value, null, MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT);
    EasyMock.expectLastCall().andReturn(false);
    service.put(key2, value, null, MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    EasyMock.expectLastCall().andReturn(false);
    service.put(key, value,
                Expiration.byDeltaSeconds(123),
                MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    EasyMock.expectLastCall().andReturn(true);
    Date now = new Date();
    service.put(key, value,
                Expiration.onDate(now),
                MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    EasyMock.expectLastCall().andReturn(true);
    service.put(key, value,
                Expiration.byDeltaMillis(12345678),
                MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    EasyMock.expectLastCall().andReturn(true);
    control.replay();
    cache.put(key, value);
    properties.put(GCacheFactory.SET_POLICY, MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    cache = new GCache(properties);
    cache.put(key, value);
    properties.put(GCacheFactory.SET_POLICY, MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT);
    cache = new GCache(properties);
    cache.put(key, value);
    properties.put(GCacheFactory.SET_POLICY, MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    cache = new GCache(properties);
    cache.put(key2, value);
    properties.put(GCacheFactory.THROW_ON_PUT_FAILURE, Boolean.TRUE);
    properties.put(GCacheFactory.SET_POLICY, MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT);
    cache = new GCache(properties);
    try {
      cache.put(key, value);
      fail("Expected exception");
    } catch (GCacheException ex) {
    }
    properties.put(GCacheFactory.SET_POLICY, MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    cache = new GCache(properties);
    try {
      cache.put(key2, value);
      fail("Expected exception");
    } catch (GCacheException ex) {
    }
    properties.put(GCacheFactory.EXPIRATION_DELTA, 123);
    cache = new GCache(properties);
    cache.put(key, value);
    properties.put(GCacheFactory.EXPIRATION, now);
    properties.remove(GCacheFactory.EXPIRATION_DELTA);
    cache = new GCache(properties);
    cache.put(key, value);
    properties.put(GCacheFactory.EXPIRATION_DELTA_MILLIS, 12345678);
    properties.remove(GCacheFactory.EXPIRATION);
    cache = new GCache(properties);
    cache.put(key, value);
    control.verify();
  }

  /**
   * Tests that a putAll operation passes to the MemcacheService correctly.
   */
  @SuppressWarnings("unchecked")
  public void testPutAll() throws Exception {
    Map map = new HashMap();
    map.put(key, value);
    map.put(key2, value2);
    Set failureSet = new HashSet();
    failureSet.add(key);
    service.putAll(map, null, MemcacheService.SetPolicy.SET_ALWAYS);
    EasyMock.expectLastCall().andReturn(map.keySet());
    service.putAll(map, null, MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    EasyMock.expectLastCall().andReturn(map.keySet());
    service.putAll(map, null, MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT);
    EasyMock.expectLastCall().andReturn(failureSet);
    service.putAll(map, null, MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    EasyMock.expectLastCall().andReturn(failureSet);
    service.putAll(map, null, MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT);
    EasyMock.expectLastCall().andReturn(failureSet);
    service.putAll(map, null, MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    EasyMock.expectLastCall().andReturn(failureSet);
    service.putAll(map, Expiration.byDeltaSeconds(123),
                MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    EasyMock.expectLastCall().andReturn(map.keySet());
    Date now = new Date();
    service.putAll(map, Expiration.onDate(now),
                MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    EasyMock.expectLastCall().andReturn(map.keySet());
    service.putAll(map, Expiration.byDeltaMillis(12345678),
                MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    EasyMock.expectLastCall().andReturn(map.keySet());
    control.replay();
    cache.putAll(map);
    properties.put(GCacheFactory.SET_POLICY, MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    cache = new GCache(properties);
    cache.putAll(map);

    properties.put(GCacheFactory.SET_POLICY, MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT);
    cache = new GCache(properties);
    cache.putAll(map);
    properties.put(GCacheFactory.SET_POLICY, MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    cache = new GCache(properties);
    cache.putAll(map);
    properties.put(GCacheFactory.THROW_ON_PUT_FAILURE, Boolean.TRUE);

    properties.put(GCacheFactory.SET_POLICY, MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT);
    cache = new GCache(properties);
    try {
      cache.putAll(map);
      fail("Expected exception");
    } catch (GCacheException ex) {
    }
    properties.put(GCacheFactory.SET_POLICY, MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT);
    cache = new GCache(properties);
    try {
      cache.putAll(map);
      fail("Expected exception");
    } catch (GCacheException ex) {
    }
    properties.put(GCacheFactory.EXPIRATION_DELTA, 123);
    cache = new GCache(properties);
    cache.putAll(map);
    properties.put(GCacheFactory.EXPIRATION, now);
    properties.remove(GCacheFactory.EXPIRATION_DELTA);
    cache = new GCache(properties);
    cache.putAll(map);
    properties.put(GCacheFactory.EXPIRATION_DELTA_MILLIS, 12345678);
    properties.remove(GCacheFactory.EXPIRATION);
    cache = new GCache(properties);
    cache.putAll(map);
    control.verify();
  }

  /**
   * Tests that a peek operation passes to the MemcacheService correctly.
   */
  public void testPeek() throws Exception {
    service.get(key);
    EasyMock.expectLastCall().andReturn(value);
    control.replay();
    assertEquals(value, cache.peek(key));
    control.verify();
  }

  /**
   * Tests that a get operation passes to the MemcacheService correctly.
   */
  public void testGet() throws Exception {
    service.get(key);
    EasyMock.expectLastCall().andReturn(value);
    control.replay();
    assertEquals(value, cache.get(key));
    control.verify();
  }

  /**
   * Tests that a getAll operation passes to the MemcacheService correctly.
   */
  @SuppressWarnings("unchecked")
  public void testGetAll() throws Exception {
    Map map = new HashMap();
    map.put(key, value);
    map.put(key2, value2);
    Collection keys = new ArrayList();
    keys.add(key);
    keys.add(key2);
    service.getAll(keys);
    EasyMock.expectLastCall().andReturn(map);
    control.replay();
    assertEquals(map, cache.getAll(keys));
    control.verify();
  }

  /**
   * Tests that a containsKey operation passes to the MemcacheService
   * correctly.
   */
  public void testContainsKey() throws Exception {
    service.contains(key);
    EasyMock.expectLastCall().andReturn(true);
    service.contains(key2);
    EasyMock.expectLastCall().andReturn(false);
    control.replay();
    assertTrue(cache.containsKey(key));
    assertFalse(cache.containsKey(key2));
    control.verify();
  }

  /**
   * Tests that a clear operation passes to the MemcacheService correctly.
   */
  public void testClear() throws Exception {
    service.clearAll();
    control.replay();
    cache.clear();
    control.verify();
  }

  /**
   * Tests that a getCacheEntry operation passes to the MemcacheService
   *  correctly.
   */
  public void testGetCacheEntry() throws Exception {
    service.get(key);
    EasyMock.expectLastCall().andReturn(value);
    service.get(key2);
    EasyMock.expectLastCall().andReturn(null);
    service.contains(key2);
    EasyMock.expectLastCall().andReturn(true);
    service.get(key);
    EasyMock.expectLastCall().andReturn(null);
    service.contains(key);
    EasyMock.expectLastCall().andReturn(false);
    control.replay();

    assertEquals(new GCacheEntry(cache, key, value), cache.getCacheEntry(key));
    assertEquals(new GCacheEntry(cache, key2, null), cache.getCacheEntry(key2));
    assertNull(cache.getCacheEntry(key));
    control.verify();
  }

  /**
   * Tests that remove operations pass and return values to the
   * MemcacheService correctly.
   */
  public void testRemove() throws Exception {
    service.get(key);
    EasyMock.expectLastCall().andReturn(value);
    service.delete(key);
    EasyMock.expectLastCall().andReturn(true);
    service.get(key);
    EasyMock.expectLastCall().andReturn(null);
    service.delete(key);
    EasyMock.expectLastCall().andReturn(false);
    service.get(key2);
    EasyMock.expectLastCall().andReturn(null);
    service.delete(key2);
    EasyMock.expectLastCall().andReturn(true);
    control.replay();
    assertEquals(value, cache.remove(key));
    assertNull(cache.remove(key));
    assertNull(cache.remove(key2));
    control.verify();
  }

  /**
   * Tests that GetCacheStatistics return a statistics object that correctly
   * returns stats from the stats object returned by the MemcacheService.
   */
  public void testGetCacheStatistics() throws Exception {
    service.getStatistics();
    EasyMock.expectLastCall().andReturn(stats);
    stats.getItemCount();
    EasyMock.expectLastCall().andReturn(40);
    stats.getHitCount();
    EasyMock.expectLastCall().andReturn(20);
    stats.getMissCount();
    EasyMock.expectLastCall().andReturn(3);
    control.replay();
    CacheStatistics statistics = cache.getCacheStatistics();
    assertEquals(40, statistics.getObjectCount());
    assertEquals(20, statistics.getCacheHits());
    assertEquals(3, statistics.getCacheMisses());
    try {
      statistics.clearStatistics();
      fail("Expected Exception.");
    } catch (UnsupportedOperationException ex) {
    }
    control.verify();
  }

  /**
   * Tests that size operations pass and return values to the
   * MemcacheService correctly.
   */
  public void testSize() throws Exception {
    service.getStatistics();
    EasyMock.expectLastCall().andReturn(stats);
    stats.getItemCount();
    EasyMock.expectLastCall().andReturn(30);
    control.replay();
    assertEquals(30, cache.size());
    control.verify();
  }

  /**
   * Tests that isEmpty operations pass and return values to the
   * MemcacheService correctly.
   */
  public void testIsEmpty() throws Exception {
    service.getStatistics();
    EasyMock.expectLastCall().andReturn(stats);
    stats.getItemCount();
    EasyMock.expectLastCall().andReturn(30);
    service.getStatistics();
    EasyMock.expectLastCall().andReturn(stats);
    stats.getItemCount();
    EasyMock.expectLastCall().andReturn(0);
    control.replay();
    assertFalse(cache.isEmpty());
    assertTrue(cache.isEmpty());
    control.verify();
  }

  public void testNamespaceSetting() throws Exception {
    Map customProperties = new HashMap();
    customProperties.put(GCacheFactory.MEMCACHE_SERVICE, service);
    customProperties.put(GCacheFactory.NAMESPACE, "ns");

    control.replay();
    cache = new GCache(customProperties);
    control.verify();
  }

  /**
   * Tests that all methods that are not expected to be implemented throw the
   * correct exception.
   */
  public void testUnsupportedOperations() throws Exception {
    try {
      cache.containsValue(null);
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      cache.entrySet();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException ex) {
    }
     try {
      cache.keySet();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException ex) {
    }
     try {
      cache.load(null);
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException ex) {
    }
     try {
      cache.loadAll(null);
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException ex) {
    }
     try {
      cache.values();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException ex) {
    }
  }

}
