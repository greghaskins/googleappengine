// Copyright 2007 Google Inc. All rights reserved.

package com.google.appengine.api.datastore;

import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.api.DatastorePb.NextRequest;
import com.google.apphosting.api.DatastorePb.QueryResult;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Concrete implementation of QueryResultsSource which knows how to
 * make callbacks back into the datastore to retrieve more entities
 * for the specified cursor.
 *
 */
class QueryResultsSourceImpl implements QueryResultsSource {
  static Logger logger = Logger.getLogger(QueryResultsSourceImpl.class.getName());
  private static final int AT_LEAST_ONE = -1;
  private static final String DISABLE_CHUNK_SIZE_WARNING_SYS_PROP =
      "appengine.datastore.disableChunkSizeWarning";
  private static final int CHUNK_SIZE_WARNING_RESULT_SET_SIZE_THRESHOLD = 1000;
  private static final long MAX_CHUNK_SIZE_WARNING_FREQUENCY_MS = 1000 * 60 * 5;
  static final AtomicLong lastChunkSizeWarning = new AtomicLong(0);

  private final ApiConfig apiConfig;
  private final int chunkSize;
  private final int offset;
  private final Transaction txn;

  private Future<QueryResult> nextResult;
  private int skippedResults;
  private int totalResults = 0;

  public QueryResultsSourceImpl(ApiConfig apiConfig, FetchOptions fetchOptions, Transaction txn,
      Future<QueryResult> firstResult) {
    this.apiConfig = apiConfig;
    this.chunkSize = fetchOptions.getChunkSize() != null ?
        fetchOptions.getChunkSize() : AT_LEAST_ONE;
    this.offset = fetchOptions.getOffset() != null ?
        fetchOptions.getOffset() : 0;
    this.txn = txn;
    this.nextResult = firstResult;
    this.skippedResults = 0;
  }

  @Override
  public boolean hasMoreEntities() {
    return nextResult != null;
  }

  @Override
  public int getNumSkipped() {
    return skippedResults;
  }

  @Override
  public Cursor loadMoreEntities(List<Entity> buffer) {
    return loadMoreEntities(AT_LEAST_ONE, buffer);
  }

  @Override
  public Cursor loadMoreEntities(int numberToLoad, List<Entity> buffer) {
    TransactionImpl.ensureTxnActive(txn);
    if (nextResult != null) {
      if (numberToLoad == 0 &&
          offset <= skippedResults) {
        return null;
      }

      int previousSize = buffer.size();
      QueryResult res = FutureHelper.quietGet(nextResult);
      nextResult = null;
      processQueryResult(res, buffer);

      if (res.isMoreResults()) {
        NextRequest req = new NextRequest();
        req.getMutableCursor().copyFrom(res.getCursor());
        if (res.hasCompiledCursor()) {
          req.setCompile(true);
        }

        boolean setCount = true;
        if (numberToLoad <= 0) {
          setCount = false;
          if (chunkSize != AT_LEAST_ONE) {
            req.setCount(chunkSize);
          }
          if (numberToLoad == AT_LEAST_ONE) {
            numberToLoad = 1;
          }
        }

        while (
            (skippedResults < offset ||
            buffer.size() - previousSize < numberToLoad) &&
            res.isMoreResults()) {
          if (skippedResults < offset) {
            req.setOffset(offset - skippedResults);
          } else {
            req.clearOffset();
          }
          if (setCount) {
            req.setCount(Math.max(chunkSize, numberToLoad - buffer.size() + previousSize));
          }
          res = new QueryResult();
          DatastoreApiHelper.makeSyncCall(apiConfig, "Next", req, res);
          processQueryResult(res, buffer);
        }

        if (res.isMoreResults()) {
          if (chunkSize != AT_LEAST_ONE) {
            req.setCount(chunkSize);
          } else {
            req.clearCount();
          }
          req.clearOffset();
          nextResult = DatastoreApiHelper.makeAsyncCall(apiConfig, "Next", req, new QueryResult());
        }
      }
      return res.hasCompiledCursor() ? new Cursor(res.getCompiledCursor()) : null;
    }
    return null;
  }

  /**
   * Helper function to process the query results.
   *
   * This function adds results to the given buffer and updates {@link
   * #skippedResults}.
   *
   * @param res The {@link QueryResult} to process
   * @param buffer the buffer to which to add results
   */
  private void processQueryResult(QueryResult res, List<Entity> buffer) {
    skippedResults += res.getSkippedResults();
    for (EntityProto entityProto : res.results()) {
      buffer.add(EntityTranslator.createFromPb(entityProto));
    }
    totalResults += res.resultSize();
    if (chunkSize == AT_LEAST_ONE && totalResults > CHUNK_SIZE_WARNING_RESULT_SET_SIZE_THRESHOLD &&
        System.getProperty(DISABLE_CHUNK_SIZE_WARNING_SYS_PROP) == null) {
      logChunkSizeWarning();
    }
  }

  void logChunkSizeWarning() {
    long now = System.currentTimeMillis();
    if ((now - lastChunkSizeWarning.get()) < MAX_CHUNK_SIZE_WARNING_FREQUENCY_MS) {
      return;
    }
    logger.warning(
        "This query does not have a chunk size set in FetchOptions and has returned over " +
            CHUNK_SIZE_WARNING_RESULT_SET_SIZE_THRESHOLD + " results.  If result sets of this "
            + "size are common for this query, consider setting a chunk size to improve "
            + "performance.\n  To disable this warning set the following system property in "
            + "appengine-web.xml (the value of the property doesn't matter): '"
            + DISABLE_CHUNK_SIZE_WARNING_SYS_PROP + "'");
    lastChunkSizeWarning.set(now);
  }
}
