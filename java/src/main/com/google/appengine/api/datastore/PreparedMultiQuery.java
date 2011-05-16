// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.api.datastore;

import com.google.appengine.api.datastore.EntityProtoComparators.EntityProtoComparator;
import com.google.appengine.api.datastore.Query.SortPredicate;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.api.DatastorePb.Query.Order;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A {@link PreparedQuery} implementation for use with {@link MultiQueryBuilder}.
 *
 * We run each successively generated list of queries returned by the
 * {@link MultiQueryBuilder} as they are needed and concatenate the result.
 *
 * If a list of queries contains more than one query we build a
 * {@link Comparator} based on the sort predicates of the base query. We then
 * use this {@link Comparator} to produce an appropriately ordered sequence of
 * results that contains the results from each sub-query. As each sub-query
 * produces results that are already sorted we simply use a
 * {@link PriorityQueue} to merge the results from the sub-query as new results
 * are requested.
 *
 */
class PreparedMultiQuery extends BasePreparedQuery.UncompilablePreparedQuery {
  private final ApiConfig apiConfig;
  private final DatastoreServiceConfig datastoreServiceConfig;
  private final MultiQueryBuilder queryBuilder;
  private final EntityComparator entityComparator;
  private final Transaction txn;

  /**
   * @param apiConfig The api config to use.
   * @param datastoreServiceConfig The datastore service config to use.
   * @param queryBuilder The source of queries to execute.
   * @param txn The txn in which all queries should execute. Can be null.
   *
   * @throws IllegalArgumentException if this multi-query required in memory
   * sorting and the base query is both a keys-only query and sorted by anything
   * other than its key.
   */
  PreparedMultiQuery(ApiConfig apiConfig, DatastoreServiceConfig datastoreServiceConfig,
      MultiQueryBuilder queryBuilder, Transaction txn) {
    this.apiConfig = apiConfig;
    this.datastoreServiceConfig = datastoreServiceConfig;
    this.txn = txn;
    this.queryBuilder = queryBuilder;

    if (queryBuilder.hasParallelQueries()) {
      if (queryBuilder.isKeysOnly()) {
        for (SortPredicate sp : queryBuilder.getSortPredicates()) {
          if (!sp.getPropertyName().equals(Entity.KEY_RESERVED_PROPERTY)) {
            throw new IllegalArgumentException(
                "The provided keys-only multi-query needs to perform some " +
                "sorting in memory.  As a result, this query can only be " +
                "sorted by the key property as this is the only property " +
                "that is available in memory.");
          }
        }
      }
      this.entityComparator = new EntityComparator(queryBuilder.getSortPredicates());
    } else {
      this.entityComparator = null;
    }
  }

  /**
   * A helper function to prepare batches of queries.
   * @param queries the queries to prepare
   * @return a list of prepared queries
   */
  protected List<PreparedQuery> prepareQueries(List<Query> queries) {
    List<PreparedQuery> preparedQueries = new ArrayList<PreparedQuery>(queries.size());
    for (Query q : queries) {
      preparedQueries.add(new PreparedQueryImpl(apiConfig, datastoreServiceConfig, q, txn));
    }
    return preparedQueries;
  }

  /**
   * An iterator that will correctly process the values returned by a multiquery iterator.
   *
   * This iterator in some cases may not respect the provided FetchOptions.limit().
   */
  private class FilteredMultiQueryIterator extends AbstractIterator<Entity> {
    private final Iterator<List<Query>> multiQueryIterator;
    private final FetchOptions baseFetchOptions;
    private final Set<Key> returnedKeys = new HashSet<Key>();
    private final Set<EntityFilter> entityFilters;

    private Iterator<Entity> currentIterator = new Iterator<Entity>() {
        @Override
        public boolean hasNext() {
          return false;
        }
        @Override
        public Entity next() {
          throw new NoSuchElementException();
        }
        @Override
        public void remove() {
          throw new NoSuchElementException();
        }
    };

    public FilteredMultiQueryIterator(FetchOptions fetchOptions) {
      this.multiQueryIterator = queryBuilder.iterator();
      this.baseFetchOptions = fetchOptions;
      this.entityFilters = new HashSet<EntityFilter>(queryBuilder.getEntityFilters());

      this.entityFilters.add(new EntityFilter() {
        @Override
        public boolean apply(Entity entity) {
          return returnedKeys.add(entity.getKey());
        }
      });
    }

    /**
     * Returns fetch options that have the correct limit, or null if we have hit the limit
     */
    private FetchOptions getFetchOptions() {
      if (baseFetchOptions.getLimit() != null) {
        int limit = baseFetchOptions.getLimit() - returnedKeys.size();
        if (limit > 0) {
          return new FetchOptions(baseFetchOptions).clearLimit().limit(limit);
        } else {
          return null;
        }
      }
      return baseFetchOptions;
    }

    /**
     * Returns an iterator for the next source that has results or null if there are none.
     */
    Iterator<Entity> getNextIterator() {
      FetchOptions fetchOptions = getFetchOptions();
      if (fetchOptions == null) {
        return null;
      }

      while (multiQueryIterator.hasNext()) {
        List<PreparedQuery> queries = prepareQueries(multiQueryIterator.next());
        Iterator<Entity> result;
        if (queries.size() == 1) {
          result = queries.get(0).asIterator(fetchOptions);
        } else {
          result = makeHeapIterator(queries, fetchOptions);
        }
        if (result.hasNext()) {
          return result;
        }
      }
      return null;
    }

    @Override
    protected Entity computeNext() {
      Entity result = null;
      do {
        if (!currentIterator.hasNext()) {
          currentIterator = getNextIterator();
          if (currentIterator == null) {
            endOfData();
            return null;
          }
        }
        result = currentIterator.next();
      } while (!passesFilters(result));
      return result;
    }

    private boolean passesFilters(Entity result) {
      for(EntityFilter filter : entityFilters) {
        if (!filter.apply(result)) {
          return false;
        }
      }
      return true;
    }
  }

  static final class HeapIterator extends AbstractIterator<Entity> {
    private final PriorityQueue<EntitySource> heap;

    HeapIterator(PriorityQueue<EntitySource> heap) {
      this.heap = heap;
    }

    @Override
    protected Entity computeNext() {
      Entity result;
      result = nextResult(heap);
      if (result == null) {
        endOfData();
      }
      return result;
    }
  }

  Iterator<Entity> makeHeapIterator(List<PreparedQuery> preparedQueries,
                                              FetchOptions fetchOptions) {
    final PriorityQueue<EntitySource> heap = new PriorityQueue<EntitySource>();
    for (PreparedQuery pq : preparedQueries) {
      Iterator<Entity> iter = pq.asIterator(fetchOptions);
      if (iter.hasNext()) {
        heap.add(new EntitySource(entityComparator, iter));
      }
    }
    return new HeapIterator(heap);
  }

  /**
   * Fetch the next result from the {@link PriorityQueue} and reset the
   * datasource from which the next result was taken.
   */
  static Entity nextResult(PriorityQueue<EntitySource> availableEntitySources) {
    EntitySource current = availableEntitySources.poll();
    if (current == null) {
      return null;
    }
    Entity result = current.currentEntity;
    current.advance();
    if (current.currentEntity != null) {
      availableEntitySources.add(current);
    } else {
    }
    return result;
  }

  /**
   * Data structure that we use in conjunction with the {@link PriorityQueue}.
   * It always compares using its {@code currentEntity} field by delegating to
   * its {@code entityComparator}.
   */
  static final class EntitySource implements Comparable<EntitySource> {
    private final EntityComparator entityComparator;
    private final Iterator<Entity> source;
    private Entity currentEntity;

    EntitySource(EntityComparator entityComparator, Iterator<Entity> source) {
      this.entityComparator = entityComparator;
      this.source = source;
      if (!source.hasNext()) {
        throw new IllegalArgumentException("Source iterator has no data.");
      }
      this.currentEntity = source.next();
    }

    private void advance() {
      currentEntity = source.hasNext() ? source.next() : null;
    }

    public int compareTo(EntitySource entitySource) {
      return entityComparator.compare(currentEntity, entitySource.currentEntity);
    }
  }

  /**
   * Compares {@link Entity Entities} by delegating to an
   * {@link EntityProtoComparator}. The proto representation of all the entities
   * being compared is required to be available via
   * {@link Entity#getEntityProto()}.
   */
  static final class EntityComparator implements Comparator<Entity> {
    private final EntityProtoComparator delegate;

    EntityComparator(List<SortPredicate> sortPreds) {
      delegate = new EntityProtoComparator(sortPredicatesToOrders(sortPreds));
    }

    private static List<Order> sortPredicatesToOrders(List<SortPredicate> sortPreds) {
      List<Order> orders = new ArrayList<Order>();
      for (SortPredicate sp : sortPreds) {
        orders.add(QueryTranslator.convertSortPredicateToPb(sp));
      }
      return orders;
    }

    public int compare(Entity e1, Entity e2) {
      return delegate.compare(e1.getEntityProto(), e2.getEntityProto());
    }
  }

  public Entity asSingleEntity() throws TooManyResultsException {
    List<Entity> result = this.asList(FetchOptions.Builder.withLimit(2));
    if (result.size() == 1) {
      return result.get(0);
    } else if (result.size() > 1) {
      throw new TooManyResultsException();
    } else {
      return null;
    }
  }

  public int countEntities(FetchOptions fetchOptions) {
    FetchOptions overrideOptions = new FetchOptions(fetchOptions);
    if (fetchOptions.getOffset() != null) {
      overrideOptions.clearOffset();
      if (fetchOptions.getLimit() != null) {
        int adjustedLimit = fetchOptions.getOffset() + fetchOptions.getLimit();
        if (adjustedLimit < 0) {
          overrideOptions.clearLimit();
        } else {
          overrideOptions.limit(adjustedLimit);
        }
      }
    }

    Integer adjustedLimit = overrideOptions.getLimit();
    int result = 0;
    for (List<Query> queries : queryBuilder) {
      List<PreparedQuery> preparedQueries = prepareQueries(queries);
      for (PreparedQuery query : preparedQueries) {
        result += query.countEntities(overrideOptions);
        if (adjustedLimit != null) {
          if (result >= adjustedLimit) {
            result = adjustedLimit;
            break;
          }
          overrideOptions.limit(adjustedLimit - result);
        }
      }
    }

    return fetchOptions.getOffset() == null ?
        result : Math.max(0, result - fetchOptions.getOffset());
  }

  public Iterator<Entity> asIterator(FetchOptions fetchOptions) {

    if (fetchOptions.getOffset() != null || fetchOptions.getLimit() != null) {
      FetchOptions override = new FetchOptions(fetchOptions);
      if (fetchOptions.getOffset() != null) {
        override.clearOffset();
        if (fetchOptions.getLimit() != null) {
          override.limit(fetchOptions.getOffset() + fetchOptions.getLimit());
        }
      }
      return new SlicingIterator<Entity>(
          new FilteredMultiQueryIterator(override),
          fetchOptions.getOffset(),
          fetchOptions.getLimit());
    } else {
      return new FilteredMultiQueryIterator(fetchOptions);
    }
  }

  public List<Entity> asList(FetchOptions fetchOptions) {
    FetchOptions override = new FetchOptions(fetchOptions);
    if (override.getPrefetchSize() == null) {
      override.prefetchSize(Integer.MAX_VALUE);
    }
    if (override.getChunkSize() == null) {
      override.chunkSize(Integer.MAX_VALUE);
    }

    List<Entity> results = new ArrayList<Entity>();
    for (Entity e : asIterable(override)) {
      results.add(e);
    }
    return results;
  }
}
