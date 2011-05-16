// Copyright 2009 Google Inc. All Rights Reserved.
package com.google.appengine.api.datastore;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * A class that simply forwards {@link List} functions to a delegate and stores
 * a {@link Cursor} to return when {@link #getCursor()} is called.
 *
 * @param <T> the type of result returned by the query
 *
 */
class QueryResultListImpl<T> implements QueryResultList<T> {

  /**
   * Allows deferred computation of the {@link Cursor}.
   *
   * @see LazyList
   */
  interface CursorProvider {
    Cursor get();
  }
  private final List<T> delegate;
  private final CursorProvider cursorProvider;

  public QueryResultListImpl(List<T> delegate, CursorProvider cursorProvider) {
    this.delegate = delegate;
    this.cursorProvider = cursorProvider;
  }

  public Cursor getCursor() {
    return cursorProvider.get();
  }

  public int size() {
    return delegate.size();
  }

  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  public boolean contains(Object o) {
    return delegate.contains(o);
  }

  public Iterator<T> iterator() {
    return delegate.iterator();
  }

  public Object[] toArray() {
    return delegate.toArray();
  }

  public <T> T[] toArray(T[] ts) {
    return delegate.toArray(ts);
  }

  public boolean add(T t) {
    return delegate.add(t);
  }

  public boolean remove(Object o) {
    return delegate.remove(o);
  }

  public boolean containsAll(Collection<?> objects) {
    return delegate.containsAll(objects);
  }

  public boolean addAll(Collection<? extends T> ts) {
    return delegate.addAll(ts);
  }

  public boolean addAll(int i, Collection<? extends T> ts) {
    return delegate.addAll(i, ts);
  }

  public boolean removeAll(Collection<?> objects) {
    return delegate.removeAll(objects);
  }

  public boolean retainAll(Collection<?> objects) {
    return delegate.retainAll(objects);
  }

  public void clear() {
    delegate.clear();
  }

  @Override
  public boolean equals(Object o) {
    return delegate.equals(o);
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  public T get(int i) {
    return delegate.get(i);
  }

  public T set(int i, T t) {
    return delegate.set(i, t);
  }

  public void add(int i, T t) {
    delegate.add(i, t);
  }

  public T remove(int i) {
    return delegate.remove(i);
  }

  public int indexOf(Object o) {
    return delegate.indexOf(o);
  }

  public int lastIndexOf(Object o) {
    return delegate.lastIndexOf(o);
  }

  public ListIterator<T> listIterator() {
    return delegate.listIterator();
  }

  public ListIterator<T> listIterator(int i) {
    return delegate.listIterator(i);
  }

  public List<T> subList(int i, int i1) {
    return delegate.subList(i, i1);
  }

  @Override
  public String toString() {
    return delegate.toString();
  }
}
