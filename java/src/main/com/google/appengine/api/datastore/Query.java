// Copyright 2007 Google Inc. All rights reserved.

package com.google.appengine.api.datastore;

import com.google.appengine.api.NamespaceManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * {@link Query} encapsulates a request for zero or more {@link Entity} objects
 * out of the datastore.  It supports querying on zero or more properties,
 * querying by ancestor, and sorting.  {@link Entity} objects which match the
 * query can be retrieved in a single list, or with an unbounded iterator.
 *
 */
public final class Query implements Serializable {

  /**
   * A metadata kind that can be used to query for kinds that exist in the
   * datastore.
   */
  public static final String KIND_METADATA_KIND = "__kind__";

  /**
   * A metadata kind that can be used to query for properties that exist in the
   * datastore.
   */
  public static final String PROPERTY_METADATA_KIND = "__property__";

  /**
   * A metadata kind that can be used to query for namespaces that exist in the
   * datastore.
   */
  public static final String NAMESPACE_METADATA_KIND = "__namespace__";

  static final long serialVersionUID = 7090652715949085374L;

  /**
   * SortDirection controls the order of a sort.
   */
  public enum SortDirection { ASCENDING, DESCENDING }

  /**
   * FilterOperator specifies what type of operation you want to apply
   * to your filter.
   */
  public enum FilterOperator {
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    EQUAL("="),
    NOT_EQUAL("!="),
    IN("IN");

    private final String shortName;
    private FilterOperator(String shortName) {
      this.shortName = shortName;
    }

    @Override
    public String toString() {
      return shortName;
    }
  }

  private final String kind;
  private final List<SortPredicate> sortPredicates;
  private final List<FilterPredicate> filterPredicates;
  private Key ancestor;
  private boolean keysOnly;
  private AppIdNamespace appIdNamespace;

  private transient String fullTextSearch;

  /**
   * Create a new kindless {@link Query} that finds {@link Entity} objects.
   * Note that kindless queries are not yet supported in the Java dev
   * appserver.
   *
   * Currently the only operations supported on a kindless query are filter by
   * __key__, ancestor, and order by __key__ ascending.
   */
  public Query() {
    this((String) null);
  }

  /**
   * Create a new {@link Query} that finds {@link Entity} objects with
   * the specified {@code kind}. Note that kindless queries are not yet
   * supported in the Java dev appserver.
   *
   * @param kind the kind or null to create a kindless query
   */
  public Query(String kind) {
    this(kind, null, new ArrayList<SortPredicate>(), new ArrayList<FilterPredicate>(), false,
        DatastoreApiHelper.getCurrentAppIdNamespace(), null);
  }

  Query(String kind, Key ancestor, List<SortPredicate> sortPreds,
      List<FilterPredicate> filterPreds, boolean keysOnly, AppIdNamespace appIdNamespace,
      String fullTextSearch) {
    this.kind = kind;
    this.ancestor = ancestor;
    this.sortPredicates = sortPreds;
    this.filterPredicates = filterPreds;
    this.keysOnly = keysOnly;
    this.appIdNamespace = appIdNamespace;
    this.fullTextSearch = fullTextSearch;
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    if (appIdNamespace == null) {
      if (ancestor != null) {
        appIdNamespace = ancestor.getAppIdNamespace();
      } else {
        appIdNamespace = new AppIdNamespace(DatastoreApiHelper.getCurrentAppId(), "");
      }
    }
  }

  /**
   * Create a new {@link Query} that finds {@link Entity} objects with
   * the specified {@code Key} as an ancestor.
   *
   * @param ancestor the ancestor key or null
   * @throws IllegalArgumentException If ancestor is not complete.
   */
  public Query(Key ancestor) {
    this((String) null);
    if (ancestor != null) {
      setAncestor(ancestor);
    }
  }

  /**
   * Create a new {@link Query} that finds {@link Entity} objects with
   * the specified {@code kind} and the specified {@code ancestor}. Note that
   * kindless queries are not yet supported in the Java dev appserver.
   *
   * @param kind the kind or null to create a kindless query
   * @param ancestor the ancestor key or null
   * @throws IllegalArgumentException If the ancestor is not complete.
   */
  public Query(String kind, Key ancestor) {
    this(kind);
    if (ancestor != null) {
      setAncestor(ancestor);
    }
  }

  /**
   * Only {@link Entity} objects whose kind matches this value will be
   * returned.
   */
  public String getKind() {
    return kind;
  }

  /**
   * Returns the AppIdNamespace that is being queried.
   * <p>The AppIdNamespace is set at construction time of this
   * object using the {@link NamespaceManager} to retrieve the
   * current namespace.
   */
  AppIdNamespace getAppIdNamespace() {
    return appIdNamespace;
  }

  /**
   * Gets the current ancestor for this query, or null if there is no
   * ancestor specified.
   */
  public Key getAncestor() {
    return ancestor;
  }

  /**
   * Sets an ancestor for this query.
   *
   * This restricts the query to only return result entities that are
   * descended from a given entity. In other words, all of the results
   * will have the ancestor as their parent, or parent's parent, or
   * etc.
   *
   * If null is specified, unsets any previously-set ancestor.  Passing
   * {@code null} as a parameter does not query for entities without
   * ancestors (this type of query is not currently supported).
   *
   * @return {@code this} (for chaining)
   *
   * @throws IllegalArgumentException If the ancestor key is incomplete, or if
   * you try to unset an ancestor and have not set a kind, or if you try to
   * unset an ancestor and have not previously set an ancestor.
   */
  public Query setAncestor(Key ancestor) {
    if (ancestor != null && !ancestor.isComplete()) {
      throw new IllegalArgumentException(ancestor + " is incomplete.");
    } else if (ancestor == null) {
      if (this.ancestor == null) {
        throw new IllegalArgumentException(
            "Cannot clear ancestor unless ancestor has already been set");
      }
    }
    if (ancestor != null) {
      if (!ancestor.getAppIdNamespace().equals(appIdNamespace)) {
        throw new IllegalArgumentException(
            "Namespace of ancestor key and query must match.");
      }
    }
    this.ancestor = ancestor;
    return this;
  }

  /**
   * Add a filter on the specified property.
   *
   * @param propertyName The name of the property to which the filter applies.
   * @param operator The filter operator.
   * @param value An instance of a supported datastore type.  Note that
   * entities with multi-value properties identified by {@code propertyName}
   * will match this filter if the multi-value property has at least one
   * value that matches the condition expressed by {@code operator} and
   * {@code value}.  For more information on multi-value property filtering
   * please see the
   * <a href="http://code.google.com/appengine/docs/java/datastore">
   * datastore documentation</a>.
   *
   * @return {@code this} (for chaining)
   *
   * @throws NullPointerException If {@code propertyName} or {@code operator}
   * is null.
   * @throws IllegalArgumentException If {@code value} is not of a
   * type supported by the datastore.  See
   * {@link DataTypeUtils#isSupportedType(Class)}.  Note that unlike
   * {@link Entity#setProperty(String, Object)}, you cannot provide
   * a {@link Collection} containing instances of supported types
   * to this method.
   */
  public Query addFilter(String propertyName, FilterOperator operator, Object value) {
    filterPredicates.add(new FilterPredicate(propertyName, operator, value));
    return this;
  }

  /**
   * Returns an unmodifiable list of the current filter predicates.
   */
  public List<FilterPredicate> getFilterPredicates() {
    return Collections.unmodifiableList(filterPredicates);
  }

  /**
   * Specify how the query results should be sorted.
   *
   * The first call to addSort will register the property that will
   * serve as the primary sort key.  A second call to addSort will set
   * a secondary sort key, etc.
   *
   * This method will always sort in ascending order.  To control the
   * order of the sort, use {@link #addSort(String,SortDirection)}.
   *
   * Note that entities with multi-value properties identified by
   * {@code propertyName} will be sorted by the smallest value in the list.
   * For more information on sorting properties with multiple values please see
   * the <a href="http://code.google.com/appengine/docs/java/datastore">
   * datastore documentation</a>.
   *
   * @return {@code this} (for chaining)
   *
   * @throws NullPointerException If any argument is null.
   */
  public Query addSort(String propertyName) {
    return addSort(propertyName, SortDirection.ASCENDING);
  }

  /**
   * Specify how the query results should be sorted.
   *
   * The first call to addSort will register the property that will
   * serve as the primary sort key.  A second call to addSort will set
   * a secondary sort key, etc.
   *
   * Note that if {@code direction} is {@link SortDirection#ASCENDING},
   * entities with multi-value properties identified by
   * {@code propertyName} will be sorted by the smallest value in the list.  If
   * {@code direction} is {@link SortDirection#DESCENDING}, entities with
   * multi-value properties identified by {@code propertyName} will be sorted
   * by the largest value in the list.  For more information on sorting
   * properties with multiple values please see
   * the <a href="http://code.google.com/appengine/docs/java/datastore">
   * datastore documentation</a>.
   *
   * @return {@code this} (for chaining)
   *
   * @throws NullPointerException If any argument is null.
   */
  public Query addSort(String propertyName, SortDirection direction) {
    sortPredicates.add(new SortPredicate(propertyName, direction));
    return this;
  }

  /**
   * Returns an unmodifiable list of the current sort predicates.
   */
  public List<SortPredicate> getSortPredicates() {
    return Collections.unmodifiableList(sortPredicates);
  }

  /**
   * Makes this query fetch and return only keys, not full entities.
   *
   * @return {@code this} (for chaining)
   */
  public Query setKeysOnly() {
    keysOnly = true;
    return this;
  }

  /**
   * Returns true if this query will fetch and return keys only, false if it
   * will fetch and return full entities.
   */
  public boolean isKeysOnly() {
    return keysOnly;
  }

  /**
   * Returns the query's full text search string.
   */
  String getFullTextSearch() {
    return fullTextSearch;
  }

  /**
   * Sets the query's full text search string.
   *
   * @return {@code this} (for chaining)
   */
  Query setFullTextSearch(String fullTextSearch) {
    this.fullTextSearch = fullTextSearch;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Query query = (Query) o;

    if (keysOnly != query.keysOnly) {
      return false;
    }
    if (ancestor != null ? !ancestor.equals(query.ancestor) : query.ancestor != null) {
      return false;
    }
    if (!appIdNamespace.equals(query.appIdNamespace)) {
      return false;
    }
    if (!filterPredicates.equals(query.filterPredicates)) {
      return false;
    }
    if (!kind.equals(query.kind)) {
      return false;
    }
    if (!sortPredicates.equals(query.sortPredicates)) {
      return false;
    }
    if (fullTextSearch != null ? !fullTextSearch.equals(query.fullTextSearch) :
        query.fullTextSearch != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = kind.hashCode();
    result = 31 * result + sortPredicates.hashCode();
    result = 31 * result + filterPredicates.hashCode();
    result = 31 * result + (ancestor != null ? ancestor.hashCode() : 0);
    result = 31 * result + (keysOnly ? 1 : 0);
    result = 31 * result + appIdNamespace.hashCode();
    result = 31 * result + (fullTextSearch != null ? fullTextSearch.hashCode() : 0);
    return result;
  }

  /**
   * Outputs a SQL like string representing the query.
   */
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder("SELECT " + (keysOnly ? "__key__" : "*"));

    if (kind != null) {
      result.append(" FROM ");
      result.append(kind);
    }

    if (ancestor != null || !filterPredicates.isEmpty()) {
      result.append(" WHERE ");
    }

    final String AND_SEPARATOR = " AND ";
    for (FilterPredicate filter : filterPredicates) {
      result.append(filter);
      result.append(AND_SEPARATOR);
    }

    if (ancestor != null) {
      result.append("__ancestor__ is ");
      result.append(ancestor);
    } else if (!filterPredicates.isEmpty()) {
      result.delete(result.length() - AND_SEPARATOR.length(), result.length());
    }

    final String COMMA_SEPARATOR = ", ";
    if (!sortPredicates.isEmpty()) {
      result.append(" ORDER BY ");
      for (SortPredicate sort : sortPredicates) {
        result.append(sort);
        result.append(COMMA_SEPARATOR);
      }
      result.delete(result.length() - COMMA_SEPARATOR.length(), result.length());
    }
    return result.toString();
  }

  /**
   * SortPredicate is a data container that holds a single sort
   * predicate.
   */
  public static final class SortPredicate implements Serializable {
    static final long serialVersionUID = -623786024456258081L;
    private final String propertyName;
    private final SortDirection direction;

    public SortPredicate(String propertyName, SortDirection direction) {
      if (propertyName == null) {
        throw new NullPointerException("Property name was null");
      }

      if (direction == null) {
        throw new NullPointerException("Direction was null");
      }

      this.propertyName = propertyName;
      this.direction = direction;
    }

    /**
     * Gets the name of the property to sort on.
     */
    public String getPropertyName() {
      return propertyName;
    }

    /**
     * Gets the direction of the sort.
     */
    public SortDirection getDirection() {
      return direction;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      SortPredicate that = (SortPredicate) o;

      if (direction != that.direction) {
        return false;
      }
      if (!propertyName.equals(that.propertyName)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result;
      result = propertyName.hashCode();
      result = 31 * result + direction.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return propertyName + (direction == SortDirection.DESCENDING ? " DESC" : "");
    }
  }

  /**
   * FilterPredicate is a data container that holds a single filter
   * predicate.
   */
  public static final class FilterPredicate implements Serializable {
    static final long serialVersionUID = 7681475799401864259L;
    private final String propertyName;
    private final FilterOperator operator;
    private final Object value;

    /**
     * Constructs a filter predicate from the given parameters.
     *
     * @param propertyName the name of the property on which to filter
     * @param operator the operator to apply
     * @param value A single instances of a supported type or if {@code
     * operator} is {@link FilterOperator#IN} a non-empty {@link Iterable}
     * object containing instances of supported types.
     *
     * @throws IllegalArgumentException If the provided filter values are not
     * supported.
     *
     * @see DataTypeUtils#isSupportedType(Class)
     */

    public FilterPredicate(String propertyName, FilterOperator operator, Object value) {
      if (propertyName == null) {
        throw new NullPointerException("Property name was null");
      } else if (operator == null) {
        throw new NullPointerException("Operator was null");
      } else if (operator == FilterOperator.IN) {
        if (!(value instanceof Collection<?>) && value instanceof Iterable<?>) {
          List<Object> newValue = new ArrayList<Object>();
          for (Object val : (Iterable<?>) value) {
            newValue.add(val);
          }
          value = newValue;
        }
        DataTypeUtils.checkSupportedValue(propertyName, value, true, true);
      } else {
        DataTypeUtils.checkSupportedValue(propertyName, value, false, false);
      }
      this.propertyName = propertyName;
      this.operator = operator;
      this.value = value;
    }

    /**
     * Gets the name of the property to be filtered on.
     */
    public String getPropertyName() {
      return propertyName;
    }

    /**
     * Gets the operator describing how to apply the filter.
     */
    public FilterOperator getOperator() {
      return operator;
    }

    /**
     * Gets the argument to the filter operator.
     */
    public Object getValue() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      FilterPredicate that = (FilterPredicate) o;

      if (operator != that.operator) {
        return false;
      }
      if (!propertyName.equals(that.propertyName)) {
        return false;
      }
      if (value != null ? !value.equals(that.value) : that.value != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result;
      result = propertyName.hashCode();
      result = 31 * result + operator.hashCode();
      result = 31 * result + (value != null ? value.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return propertyName + " " + operator.toString() + " " +
             (value != null ? value.toString() : "NULL");
    }
  }
}
