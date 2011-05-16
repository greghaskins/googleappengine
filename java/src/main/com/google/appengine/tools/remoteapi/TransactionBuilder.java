// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.tools.remoteapi;

import com.google.protobuf.ByteString;
import com.google.apphosting.utils.remoteapi.RemoteApiPb;
import com.google.storage.onestore.v3.OnestoreEntity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * An in-progress transaction that will be sent via the remote API on commit.
 */
class TransactionBuilder {

  /**
   * A map containing a copy of each entity that we retrieved from the
   * datastore during this transaction. On commit, we will assert
   * that these entities haven't changed. If the value is null, the
   * datastore didn't return any entity for the given key, and we will
   * assert that the entity doesn't exist at commit time.
   */
  private final Map<ByteString, OnestoreEntity.EntityProto> getCache =
      new HashMap<ByteString, OnestoreEntity.EntityProto>();

  /**
   * A map from an entity's key to the entity that should be saved when
   * this transaction commits. If the value is null, the entity should
   * be deleted.
   */
  private final Map<ByteString, OnestoreEntity.EntityProto> updates =
      new HashMap<ByteString, OnestoreEntity.EntityProto>();

  /**
   * Returns true if we've cached the presence or absence of this entity.
   */
  public boolean isCachedEntity(OnestoreEntity.Reference key) {
    return getCache.containsKey(key.toByteString());
  }

  /**
   * Saves the original value of an entity (as returned by the datastore)
   * to the local cache.
   */
  public void addEntityToCache(OnestoreEntity.EntityProto entityPb) {
    ByteString key = entityPb.getKey().toByteString();
    if (getCache.containsKey(key)) {
      throw new IllegalStateException("shouldn't load the same entity twice within a transaction");
    }
    getCache.put(key, entityPb);
  }

  /**
   * Caches the absence of an entity (according to the datastore).
   */
  public void addEntityAbsenceToCache(OnestoreEntity.Reference key) {
    ByteString keyBytes = key.toByteString();
    if (getCache.containsKey(keyBytes)) {
      throw new IllegalStateException("shouldn't load the same entity twice within a transaction");
    }
    getCache.put(keyBytes, (OnestoreEntity.EntityProto) null);
  }

  /**
   * Returns a cached entity, or null if the entity's absence was cached.
   */
  @Nullable
  public OnestoreEntity.EntityProto getCachedEntity(OnestoreEntity.Reference key) {
    ByteString keyBytes = key.toByteString();
    if (!getCache.containsKey(keyBytes)) {
      throw new IllegalStateException("entity's status unexpectedly not in cache");
    }
    return getCache.get(keyBytes);
  }

  public void putEntityOnCommit(OnestoreEntity.EntityProto entity) {
    updates.put(entity.getKey().toByteString(), entity);
  }

  public void deleteEntityOnCommit(OnestoreEntity.Reference key) {
    updates.put(key.toByteString(), null);
  }

  /**
   * Creates a request to perform this transaction on the server.
   */
  public RemoteApiPb.TransactionRequest makeCommitRequest() {
    RemoteApiPb.TransactionRequest result = new RemoteApiPb.TransactionRequest();
    for (Map.Entry<ByteString, OnestoreEntity.EntityProto> entry : getCache.entrySet()) {
      if (entry.getValue() == null) {
        result.addPrecondition(makeEntityNotFoundPrecondition(entry.getKey()));
      } else {
        result.addPrecondition(makeEqualEntityPrecondition(entry.getValue()));
      }
    }
    for (Map.Entry<ByteString, OnestoreEntity.EntityProto> entry : updates.entrySet()) {
      OnestoreEntity.EntityProto entityPb = entry.getValue();
      if (entityPb == null) {
        result.getMutableDeletes().addKey().mergeFrom(entry.getKey().toByteArray());
      } else {
        result.getMutablePuts().addEntity(entityPb);
      }
    }
    return result;
  }

  private static RemoteApiPb.TransactionRequest.Precondition makeEntityNotFoundPrecondition(
      ByteString key) {
    OnestoreEntity.Reference ref = new OnestoreEntity.Reference();
    ref.mergeFrom(key.toByteArray());

    RemoteApiPb.TransactionRequest.Precondition result =
        new RemoteApiPb.TransactionRequest.Precondition();
    result.setKey(ref);
    return result;
  }

  private static RemoteApiPb.TransactionRequest.Precondition makeEqualEntityPrecondition(
      OnestoreEntity.EntityProto entityPb) {
    RemoteApiPb.TransactionRequest.Precondition result =
        new RemoteApiPb.TransactionRequest.Precondition();
    result.setKey(entityPb.getKey());
    result.setHashAsBytes(computeSha1(entityPb));
    return result;
  }

  private static byte[] computeSha1(OnestoreEntity.EntityProto entity) {
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("can't compute sha1 hash");
    }
    byte[] entityBytes = entity.toByteArray();
    md.update(entityBytes, 0, entityBytes.length - 1);
    return md.digest();
  }
}
