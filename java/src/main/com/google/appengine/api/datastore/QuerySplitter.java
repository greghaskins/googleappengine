// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.api.datastore;

import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortPredicate;

import java.util.List;
import java.util.Set;

/**
 * Interface describing an object than knows how to split {@link
 * FilterPredicate}s from a {@link Query} into list of {@link
 * QuerySplitComponent}s.
 *
 */
interface QuerySplitter {
  /**
   * Removes filters from the provided list and converts them into
   * {@link QuerySplitComponent}s. All filters that are satisfied by the
   * returned {@link QuerySplitComponent}s should be removed from the given list
   * of filters.
   *
   * @param remainingFilters the filters to consider. Potentially mutated in
   * this function.
   * @param sorts the sort orders imposed on the query being split
   * @param entityFilters an out parameter that should be populated with entity
   * filters that need to be applied to the result set.
   * Add to this set if extra filtering is needed to create a valid result set.
   * @return the {@link QuerySplitComponent}s that have been generated or an
   * empty list.
   */
  List<QuerySplitComponent> split(List<FilterPredicate> remainingFilters,
                                  List<SortPredicate> sorts,
                                  Set<EntityFilter> entityFilters);

}
