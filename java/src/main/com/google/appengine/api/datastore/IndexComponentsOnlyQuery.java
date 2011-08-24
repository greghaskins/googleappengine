// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.api.datastore;

import com.google.apphosting.api.DatastorePb;
import com.google.apphosting.api.DatastorePb.Query.Filter;
import com.google.apphosting.api.DatastorePb.Query.Order;
import com.google.storage.onestore.v3.OnestoreEntity.Index.Property;
import com.google.storage.onestore.v3.OnestoreEntity.Index.Property.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A query as it is actually planned on the datastore indices.
 *
 * This query should not be used to actually run a query. It is only useful
 * when determining what indices are needed to satisfy a Query
 *
 * In the production datastore, some components of the query can be fulfilled
 * natively, so before we try to determine what composite indexes this query
 * requires we want to strip out those components. An example of this is sort
 * by __key__ ascending. This can always be stripped out as all tables are
 * sorted by __key__ ascending natively
 *
 * This class also categorizes query components into groups that
 * are useful for discerning what indices are needed to satisfy it. Specifically
 * it constructs a set of the properties involved in equality filters, and also
 * constructs a list of index properties.
 */
class IndexComponentsOnlyQuery extends ValidatedQuery {
  private final Set<String> equalityProps = new HashSet<String>();
  private final List<Property> indexProps = new ArrayList<Property>();
  private boolean hasKeyProperty = false;

  public IndexComponentsOnlyQuery(DatastorePb.Query query) {
    super(query);
    removeNativelySupportedComponents();
    categorizeQuery();
  }

  private void removeNativelySupportedComponents() {

    boolean hasKeyDescOrder = false;
    if (query.orderSize() > 0) {
      Order lastOrder = query.getOrder(query.orderSize()-1);
      if (lastOrder.getProperty().equals(Entity.KEY_RESERVED_PROPERTY)) {
        if (lastOrder.getDirection() == Order.Direction.ASCENDING.getValue()) {
          query.removeOrder(query.orderSize()-1);
        } else {
          hasKeyDescOrder = true;
        }
      }
    }

    if (!hasKeyDescOrder) {
      boolean hasNonKeyInequality = false;
      for (Filter f : query.filters()) {
        if (ValidatedQuery.INEQUALITY_OPERATORS.contains(f.getOpEnum()) &&
            !Entity.KEY_RESERVED_PROPERTY.equals(f.getProperty(0).getName())) {
          hasNonKeyInequality = true;
          break;
        }
      }

      if (!hasNonKeyInequality) {
        Iterator<Filter> itr = query.filterIterator();
        while(itr.hasNext()) {
          if (itr.next().getProperty(0).getName().equals(Entity.KEY_RESERVED_PROPERTY))
            itr.remove();
        }
      }
    }
  }

  /**
   * We compare {@link Property Properties} by comparing their names.
   */
  private static final Comparator<Property> PROPERTY_NAME_COMPARATOR = new Comparator<Property>() {
    @Override
    public int compare(Property o1, Property o2) {
      return o1.getName().compareTo(o2.getName());
    }
  };

  private void categorizeQuery() {
    Set<String> ineqProps = new HashSet<String>();
    Set<String> existsProps = new HashSet<String>();
    hasKeyProperty = false;
    for (Filter filter : query.filters()) {
      String propName = filter.getProperty(0).getName();
      switch (filter.getOpEnum()) {
        case EQUAL:
          equalityProps.add(propName);
          break;
        case EXISTS:
          existsProps.add(propName);
          break;
        case GREATER_THAN:
        case GREATER_THAN_OR_EQUAL:
        case LESS_THAN:
        case LESS_THAN_OR_EQUAL:
          ineqProps.add(propName);
          break;
      }
      if (propName.equals(Entity.KEY_RESERVED_PROPERTY)) {
        hasKeyProperty = true;
      }
    }

    for (String eqProp : equalityProps) {
      indexProps.add(newIndexProperty(eqProp, Direction.ASCENDING));
    }

    Collections.sort(indexProps, PROPERTY_NAME_COMPARATOR);

    if (query.orderSize() == 0 && !ineqProps.isEmpty()) {
      indexProps.add(newIndexProperty(ineqProps.iterator().next(),
                     Direction.ASCENDING));
    }

    for (Order order : query.orders()) {
      if (order.getProperty().equals(Entity.KEY_RESERVED_PROPERTY)) {
        hasKeyProperty = true;
      }
      indexProps.add(newIndexProperty(
          order.getProperty(), Direction.valueOf(order.getDirection())));
    }

    for (String existsProp : existsProps) {
      if (!indexPropertyWithNameExists(existsProp, indexProps)) {
        indexProps.add(newIndexProperty(existsProp, Direction.ASCENDING));
      }
    }
  }

  private static boolean indexPropertyWithNameExists(
      String propName, List<Property> indexProperties) {
    for (Property indexProperty : indexProperties) {
      if (indexProperty.getName().equals(propName)) {
        return true;
      }
    }
    return false;
  }

  private static Property newIndexProperty(String name, Direction direction) {
    Property indexProperty = new Property();
    indexProperty.setName(name);
    indexProperty.setDirection(direction);
    return indexProperty;
  }

  public Set<String> getEqualityProps() {
    return equalityProps;
  }

  public boolean hasKeyProperty() {
    return hasKeyProperty;
  }

  public List<Property> getIndexProps() {
    return indexProps;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    IndexComponentsOnlyQuery that = (IndexComponentsOnlyQuery) o;

    if (!query.equals(that.query)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return query.hashCode();
  }
}