// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.api.memcache.jsr107cache;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheEntry;

/**
 */
public class GCacheEntryTest extends TestCase {

  private static final String key = "a key";
  private static final String value = "a value";
  private static final String value2 = "another value";

  private Cache cache;
  private CacheEntry entry;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    cache = EasyMock.createStrictMock(Cache.class);
    entry = new GCacheEntry(cache, key, value);

  }

  /**
   * Tests that setValue passes the correct argument to the cache and updates
   * its value correctly.
   */
  @SuppressWarnings("unchecked")
  public void testSetValue() throws Exception {
    cache.put(key, value2);
    EasyMock.expectLastCall().andReturn(value);
    EasyMock.replay(cache);
    assertEquals(value, entry.setValue(value2));
    EasyMock.verify(cache);
    assertEquals(value2, entry.getValue());
  }

  /**
   * Tests that the correct key is returned.
   */
  public void testGetKey() throws Exception {
    assertEquals(key, entry.getKey());
  }

  /**
   * Tests that a the correct value is returned.
   */
  public void testGetValue() throws Exception {
    assertEquals(value, entry.getValue());
  }

  public void testIsValid() throws Exception {
    cache.getCacheEntry(key);
    EasyMock.expectLastCall().andReturn(new GCacheEntry(cache, key, value));
    cache.getCacheEntry(key);
    EasyMock.expectLastCall().andReturn(new GCacheEntry(cache, key, value2));
    cache.getCacheEntry(key);
    EasyMock.expectLastCall().andReturn(null);
    EasyMock.replay(cache);
    assertTrue(entry.isValid());
    assertFalse(entry.isValid());
    assertFalse(entry.isValid());
    EasyMock.verify(cache);

  }

  /**
   * Tests that all methods that are not expected to be implemented throw the
   * correct exception.
   */
  public void testUnsupportedOperations() throws Exception {
    try {
      entry.getCost();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      entry.getCreationTime();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      entry.getExpirationTime();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      entry.getHits();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      entry.getLastAccessTime();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      entry.getLastUpdateTime();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      entry.getVersion();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException ex) {
    }
  }

}
