// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.api.datastore;

import com.google.appengine.api.datastore.Query.FilterPredicate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Helper class for query splitting.
 *
 * <p>This class creates a {@link MultiQueryBuilder} that will produce a
 * sequence of lists of queries. Each list of queries in the sequence should
 * have their results merged (this list could consist of a single query, which
 * can just be executed normally). The results of each list should then be
 * concatenated with the next list in the sequence.
 *
 * <p>This class guarantees that the result of merging the result in the manner
 * described above will produce a valid result set.
 *
 * <p>The algorithm employed here is as efficient as possible. It has been
 * optimized to favor concatenation over merging results in memory, as
 * concatenating results allows for greater leveraging of limit, prefetch,
 * and count parameters. This should also improve the performance slightly
 * when compared to a query splitter algorithm that attempts to merge all result
 * as the results for all queries are fetched synchronously. Even when we start
 * to use the async query framework the only loss of speed would be the time
 * saved by the first async prefetch for each set of queries. This potential
 * loss is both small and greatly out weighed by the value of respecting limit,
 * prefetch and count more accurately. It can also be eliminated by starting the
 * next round of queries before the last is done.
 *
 * <p>There are also many situations where all queries can be done sequentially.
 * In these cases we can also support sorts on keys_only queries.
 *
 * <p>This class does not preserve the order in which the filters appear in the
 * query provided it.
 *
 * <p>As the number of queries that need to be executed to generate the result
 * set is mult_i(|component_i.filters|) (which grows very fast) we rely on
 * {@link #MAX_PARALLEL_QUERIES} to limit the number of queries that need to be
 * run in parallel.
 *
 */
final class QuerySplitHelper {
  private QuerySplitHelper() {}

  private static final int MAX_PARALLEL_QUERIES = 30;
  private static final Collection<QuerySplitter> QUERY_SPLITTERS =
      Collections.synchronizedCollection(Arrays.<QuerySplitter>asList(
          new NotEqualQuerySplitter(),
          new InQuerySplitter()));

  /**
   * Splits the provided {@link Query} into a list of sub-queries though a
   * {@link MultiQueryBuilder} using the default set of {@link QuerySplitter}s.
   *
   * @return the resulting {@code MultiQueryBuilder} or null if the query could
   * not be split
   */
  static MultiQueryBuilder splitQuery(Query query) {
    return splitQuery(query, QUERY_SPLITTERS);
  }

  /**
   * Splits the provided {@link Query} into a list of sub-queries though a
   * {@link MultiQueryBuilder} using the provided {@link QuerySplitter}s.
   *
   * @return the resulting {@code MultiQueryBuilder} or null if the query could
   * not be split
   */
  static MultiQueryBuilder splitQuery(Query query, Collection<QuerySplitter> splitters) {
    List<FilterPredicate> remainingFilters =
        new LinkedList<FilterPredicate>(query.getFilterPredicates());
    List<QuerySplitComponent> components = new ArrayList<QuerySplitComponent>();
    Set<EntityFilter> entityFilters = new HashSet<EntityFilter>();
    for (QuerySplitter splitter : splitters) {
      components.addAll(splitter.split(remainingFilters, query.getSortPredicates(), entityFilters));
    }

    if (components.size() > 0) {
      return new MultiQueryBuilder(
          query, remainingFilters, entityFilters,
          convertComponents(components, query.getSortPredicates().size()));
    }
    return null;
  }

  /**
   * This function converts {@link QuerySplitComponent}s into
   * {@link MultiQueryComponent}s.
   *
   * <p>Exposed for testing purposes.
   *
   * @param components the components to convert
   * @return the converted components
   */
   static List<MultiQueryComponent> convertComponents(
      List<QuerySplitComponent> components, int numberOfSorts) {
    if (components.isEmpty()) {
      throw new IllegalArgumentException();
    }
    List<MultiQueryComponent> result =
      new ArrayList<MultiQueryComponent>(components.size());

    Collections.sort(components);

    MultiQueryComponent.Order applyToRemaining =
        numberOfSorts == 0 ? MultiQueryComponent.Order.SERIAL : null;

    int currentSortIndex = 0;
    int totalParallelQueries = 1;
    for (QuerySplitComponent component : components) {
      if ((applyToRemaining == null) && (component.getSortIndex() != currentSortIndex)) {
        if (component.getSortIndex() == currentSortIndex + 1) {
          ++currentSortIndex;
        } else {
          applyToRemaining = MultiQueryComponent.Order.PARALLEL;
        }
      }

      result.add(new MultiQueryComponent(
          applyToRemaining != null ? applyToRemaining : MultiQueryComponent.Order.SERIAL,
          component.getFilters()));

      if (applyToRemaining == MultiQueryComponent.Order.PARALLEL) {
        totalParallelQueries *= component.getFilters().size();
        if (totalParallelQueries > MAX_PARALLEL_QUERIES) {
          throw new IllegalArgumentException(
              "Splitting the provided query requires that too many subqueries are "
              + "merged in memory.");
        }
      }
    }
    return result;
  }
}
