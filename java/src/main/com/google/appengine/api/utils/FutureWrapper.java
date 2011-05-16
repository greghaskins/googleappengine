// Copyright 2009 Google Inc. All rights reserved.

package com.google.appengine.api.utils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@code FutureWrapper} is a simple {@link Future} that wraps a
 * parent {@code Future}.  This class is thread-safe.
 *
 */
abstract public class FutureWrapper<K,V> implements Future<V> {

  private final Future<K> parent;

  private boolean hasResult;
  private V result;

  private final Lock lock = new ReentrantLock();

  public FutureWrapper(Future<K> parent) {
    this.parent = parent;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return parent.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return parent.isCancelled();
  }

  @Override
  public boolean isDone() {
    return parent.isDone();
  }

  private V wrapAndCache(K data) throws ExecutionException {
    try {
      result = wrap(data);
    } catch (Exception e) {
      throw new ExecutionException(e);
    }
    hasResult = true;
    return result;
  }

  @Override
  public V get() throws InterruptedException, ExecutionException {
    lock.lock();
    try {
      if (hasResult) {
        return result;
      }
      try {
        return wrapAndCache(parent.get());
      } catch (ExecutionException ex) {
        throw new ExecutionException(convertException(ex.getCause()));
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V get(long timeout, TimeUnit unit)
      throws InterruptedException, TimeoutException, ExecutionException {
    long tryLockStart = System.currentTimeMillis();
    if (!lock.tryLock(timeout, unit)) {
      throw new TimeoutException();
    }
    try {
      if (hasResult) {
        return result;
      }
      long remainingDeadline = TimeUnit.MILLISECONDS.convert(timeout, unit) -
          (System.currentTimeMillis() - tryLockStart);
      try {
        return wrapAndCache(parent.get(remainingDeadline, TimeUnit.MILLISECONDS));
      } catch (ExecutionException ex) {
        throw new ExecutionException(convertException(ex.getCause()));
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public final int hashCode() {
    return super.hashCode();
  }

  @Override
  public final boolean equals(Object obj) {
    return super.equals(obj);
  }

  abstract protected V wrap(K key) throws Exception;
  abstract protected Throwable convertException(Throwable cause);
}
