// Copyright 2008 Google Inc. All Rights Reserved.
package com.google.appengine.api.datastore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Future;

/**
 * For now each thread is going to have its own stack.  This prevents users
 * from sharing a transaction across threads and also prevents users from
 * reliably sharing a transaction across requests that happen to be serviced
 * by the same thread.  When we start allowing users to create threads
 * we could change this implementation to allow transactions to be shared
 * across threads, but there's little point in supporting this now.
 *
 */
class TransactionStackImpl implements TransactionStack {

  private final ThreadLocalTransactionStack stack;

  public TransactionStackImpl() {
    this(new ThreadLocalTransactionStack.StaticMember());
  }

  /**
   * Just for testing.  Gives tests the opportunity to use some other
   * implementation of {@link ThreadLocalTransactionStack} that doesn't
   * maintain state across instances like
   * {@link ThreadLocalTransactionStack.StaticMember} does.
   */
  TransactionStackImpl(ThreadLocalTransactionStack stack) {
    this.stack = stack;
  }

  @Override
  public void push(Transaction txn) {
    if (txn == null) {
      throw new NullPointerException("txn cannot be null");
    }
    getStack().txns.addFirst(txn);
  }

  Transaction pop() {
    try {
      Transaction txn = getStack().txns.removeFirst();
      getStack().txnIdToFutures.remove(txn.getId());
      return txn;
    } catch (NoSuchElementException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void remove(Transaction txn) {
    if (!getStack().txns.remove(txn)) {
      throw new IllegalStateException(
          "Attempted to deregister a transaction that is not currently registered.");
    }
    getStack().txnIdToFutures.remove(txn.getId());
  }

  @Override
  public Transaction peek() {
    try {
      return getStack().txns.getFirst();
    } catch (NoSuchElementException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Transaction peek(Transaction returnedIfNoTxn) {
    LinkedList<Transaction> txns = getStack().txns;
    Transaction txn = txns.isEmpty() ? null : txns.peek();
    return txn == null ? returnedIfNoTxn : txn;
  }

  @Override
  public Collection<Transaction> getAll() {
    return new ArrayList<Transaction>(getStack().txns);
  }

  TransactionData getStack() {
    return stack.get();
  }

  @Override
  public void clearAll() {
    getStack().clear();
  }

  @Override
  public void addFuture(Transaction txn, Future<?> future) {
    getFutures(txn).add(future);
  }

  @Override
  public LinkedHashSet<Future<?>> getFutures(Transaction txn) {
    TransactionData td = getStack();
    LinkedHashSet<Future<?>> futures = td.txnIdToFutures.get(txn.getId());
    if (futures == null) {
      futures = new LinkedHashSet<Future<?>>();
      td.txnIdToFutures.put(txn.getId(), futures);
    }
    return futures;
  }

  /**
   * A wrapper for a ThreadLocal<LinkedList<Transaction>> that gives
   * us flexibility in terms of the lifecycle of the ThreadLocal
   * values.  This really just exists so that our production code can
   * use a static member and our test code can use an instance member
   * (it's easy to end up with flaky tests when your tests rely on
   * static members because it's too easy to forget to clear them
   * out).
   */
  interface ThreadLocalTransactionStack {

    TransactionData get();

    class StaticMember implements ThreadLocalTransactionStack {
      private static final ThreadLocal<TransactionData> STACK =
          new ThreadLocal<TransactionData>() {
        @Override
        protected TransactionData initialValue() {
          return new TransactionData();
        }
      };

      @Override
      public TransactionData get() {
        return STACK.get();
      }
    }
  }

  /**
   * Associates a list of {@link Transaction Transactions} (the stack) with a
   * map that ties transaction ids to a list of {@link Future Futures}.  Given a
   * given Transaction in the list, the Futures whose completion must be blocked
   * on when committing or rolling back the Transaction can be found be
   * retrieving the map value keyed by the id of the Transaction.
   */
  static final class TransactionData {
    final LinkedList<Transaction> txns = new LinkedList<Transaction>();
    final Map<String, LinkedHashSet<Future<?>>> txnIdToFutures =
        new HashMap<String, LinkedHashSet<Future<?>>>();

    void clear() {
      txns.clear();
      txnIdToFutures.clear();
    }
  }
}
