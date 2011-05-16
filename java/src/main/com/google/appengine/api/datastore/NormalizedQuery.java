package com.google.appengine.api.datastore;

import com.google.apphosting.api.DatastorePb.Query;
import com.google.apphosting.api.DatastorePb.Query.Filter;
import com.google.apphosting.api.DatastorePb.Query.Order;
import com.google.apphosting.api.DatastorePb.Query.Filter.Operator;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class NormalizedQuery {
  static final Set<Operator> INEQUALITY_OPERATORS = makeImmutableSet(
      Operator.GREATER_THAN,
      Operator.GREATER_THAN_OR_EQUAL,
      Operator.LESS_THAN,
      Operator.LESS_THAN_OR_EQUAL);

  protected final Query query;

  public NormalizedQuery(Query query) {
    this.query = query.clone();
    normalizeQuery();
  }

  public Query getQuery() {
    return query;
  }

  private void normalizeQuery() {

    Set<String> equalityProperties = new HashSet<String>();
    Set<String> inequalityProperties = new HashSet<String>();
    for (Filter f : query.filters()) {
      if ((f.propertySize() == 1) && (f.getOpEnum() == Operator.IN)) {
        f.setOp(Operator.EQUAL);
      }
      if (f.propertySize() >= 1) {
        if (f.getOpEnum() == Operator.EQUAL) {
          equalityProperties.add(f.getProperty(0).getName());
        } else if (INEQUALITY_OPERATORS.contains(f.getOpEnum())) {
          inequalityProperties.add(f.getProperty(0).getName());
        }
      }
    }

    equalityProperties.removeAll(inequalityProperties);

    for (Iterator<Order> i = query.orderIterator(); i.hasNext();) {
      if (!equalityProperties.add(i.next().getProperty())) {
        i.remove();
      }
    }

    for (Filter f : query.filters()) {
      if (f.getOpEnum() == Operator.EQUAL && f.propertySize() >= 1 &&
          f.getProperty(0).getName().equals(Entity.KEY_RESERVED_PROPERTY)) {
        query.clearOrder();
        break;
      }
    }

    boolean foundKeyOrder = false;
    for (Iterator<Order> i = query.orderIterator(); i.hasNext();) {
      String property = i.next().getProperty();
      if (foundKeyOrder) {
        i.remove();
      } else if (property.equals(Entity.KEY_RESERVED_PROPERTY)) {
        foundKeyOrder = true;
      }
    }
  }

  static <T> Set<T> makeImmutableSet(T ...of) {
    return Collections.unmodifiableSet(new HashSet<T>(Arrays.asList(of)));
  }
}