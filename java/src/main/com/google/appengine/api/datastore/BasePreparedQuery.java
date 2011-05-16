// Copyright 2009 Google Inc. All rights reserved.

package com.google.appengine.api.datastore;

import static com.google.appengine.api.datastore.FetchOptions.Builder.withDefaults;

import java.util.Iterator;

/**
 * A base implementations for a PreparedQuery.
 *
 * <p>This class forwards all calls to {@link PreparedQuery#
 * asIterator(FetchOptions)}/{@link PreparedQuery#
 * asQueryResultIterator(FetchOptions)} and {@link PreparedQuery
 * #asList(FetchOptions)}/{@link PreparedQuery#asQueryResultList(FetchOptions)}
 * which need to be implemented in a sub class.
 *
 */
abstract class BasePreparedQuery implements PreparedQuery {
  @Override
  public Iterable<Entity> asIterable(final FetchOptions fetchOptions) {
    return new Iterable<Entity>() {
      @Override
      public Iterator<Entity> iterator() {
        return asIterator(fetchOptions);
      }
    };
  }

  @Override
  public Iterable<Entity> asIterable() {
    return asIterable(withDefaults());
  }

  @Override
  public Iterator<Entity> asIterator() {
    return asIterator(withDefaults());
  }

  @Override
  public QueryResultIterable<Entity> asQueryResultIterable(final FetchOptions fetchOptions) {
    return new QueryResultIterable<Entity>() {
      @Override
      public QueryResultIterator<Entity> iterator() {
        return asQueryResultIterator(fetchOptions);
      }
    };
  }

  @Override
  public QueryResultIterable<Entity> asQueryResultIterable() {
    return asQueryResultIterable(withDefaults());
  }

  @Override
  public QueryResultIterator<Entity> asQueryResultIterator() {
    return asQueryResultIterator(withDefaults());
  }

  @Deprecated
  @Override
  public int countEntities() {
    return countEntities(withDefaults().limit(1000));
  }

  /**
   * This class forwards calls to {@code asQueryResult*} to other {@code as*}
   * functions and returns values where {@code .getCursor()} is always {@code
   * null}.
   */
  abstract static class UncompilablePreparedQuery extends BasePreparedQuery {
    @Override
    public QueryResultIterator<Entity> asQueryResultIterator(FetchOptions fetchOptions) {
      final Iterator<Entity> delegate = asIterator(fetchOptions);
      return new QueryResultIterator<Entity>() {
          @Override
          public Cursor getCursor() {
            return null;
          }

          @Override
          public boolean hasNext() {
            return delegate.hasNext();
          }

          @Override
          public Entity next() {
            return delegate.next();
          }

          @Override
          public void remove() {
            delegate.remove();
          }
        };
    }

    @Override
    public QueryResultList<Entity> asQueryResultList(FetchOptions fetchOptions) {
      QueryResultListImpl.CursorProvider cursorProvider = new QueryResultListImpl.CursorProvider() {
        @Override
        public Cursor get() {
          return null;
        }
      };
      return new QueryResultListImpl<Entity>(asList(fetchOptions), cursorProvider);
    }
  }
}
