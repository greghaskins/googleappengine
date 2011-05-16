// Copyright 2010 Google Inc. All rights reserved.
package com.google.appengine.api.taskqueue;

import com.google.appengine.api.datastore.Transaction;

import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * {@link Queue} is used to manage a task queue.
 * <p>Implementations of this interface must be threadsafe.
 *
 * <p>Queues are transactional.  If a datastore transaction is in progress when
 * {@link #add()} or {@link #add(TaskOptions)} is invoked, the task will only
 * be added to the queue if the datastore transaction successfully commits.  If
 * you want to add a task to a queue and have that operation succeed or fail
 * independently of an existing datastore transaction you can invoke
 * {@link #add(Transaction, TaskOptions)} with a {@code null} transaction
 * argument.  Note that while the addition of the task to the queue can
 * participate in an existing transaction, the execution of the task cannot
 * participate in this transaction.  In other words, when the transaction
 * commits you are guaranteed that your task will be added and run, not that
 * your task executed successfully.
 * <p> Queues may be configured in either push or pull mode, but they share the
 * same interface. However, only tasks with {@link TaskOptions.Method#PULL} may
 * be added to pull queues. The tasks in push queues must be added with one of
 * the other available methods.
 * <p>Pull mode queues do not automatically deliver tasks to the application.
 * The application is required to call {@link #leaseTasks(long, TimeUnit, long)} to
 * acquire a lease on the task and process them explicitly.  Attempting to call
 * {@link #leaseTasks(long, TimeUnit, long)} on a push queue causes a
 * {@link InvalidQueueModeException} to be thrown. When the task processing
 * is complex
 * has finished processing a task that is leased, it should call
 * {@link #deleteTask(String)}. If deleteTask is not called before the lease
 * expires, the task will again be available for lease.
 *
 * <p> Queue mode can be switched between push and pull. When switching from
 * push to pull, tasks will stay in the task queue and are available for lease,
 * but url and headers information will be ignored when returning the tasks.
 * When switching from pull to push, existing tasks will remain in the queue but
 * will fail on auto-execution because they lack a url. If the queue mode is
 * once again changed to pull, these tasks will eventually be available for
 * lease.
 *
 */
public interface Queue {
  /**
   * The default queue name.
   */
  String DEFAULT_QUEUE = "default";

  /**
   * The default queue path.
   */
  String DEFAULT_QUEUE_PATH = "/_ah/queue";

  /**
   * Returns the queue name.
   */
  String getQueueName();

  /**
   * Submits a task to this queue with an auto generated name with default
   * options.
   * <p>This method is similar to calling {@link #add(TaskOptions)} with
   * a {@link TaskOptions} object returned by
   * {@link TaskOptions.Builder#withDefaults()}.
   * @return A {@link TaskHandle}.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   * @throws UnsupportedTranslationException If chosen character encoding is unsupported.
   * @throws InvalidQueueModeException task method is {@link TaskOptions.Method#PULL} and queue is
   * push queue or vice versa.
   */
  TaskHandle add();

  /**
   * Submits a task to this queue.
   * @param taskOptions The definition of the task.
   * @return A {@link TaskHandle}.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TaskAlreadyExistsException
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   * @throws UnsupportedTranslationException If chosen character encoding is unsupported.
   * @throws InvalidQueueModeException task method is {@link TaskOptions.Method#PULL} and queue is
   * push queue or vice versa.
   */
  TaskHandle add(TaskOptions taskOptions);

  /**
   * Submits tasks to this queue.
   * Submission is not atomic i.e. if this method throws then some tasks may have been added to the
   * queue.
   * @param taskOptions An iterable over task definitions.
   * @return A list containing a {@link TaskHandle} for each added task.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TaskAlreadyExistsException if a task with the same name was previously created.
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   * @throws UnsupportedTranslationException If chosen character encoding is unsupported.
   * @throws InvalidQueueModeException task method is {@link TaskOptions.Method#PULL} and queue is
   * push queue or vice versa.
   */
  List<TaskHandle> add(Iterable<TaskOptions> taskOptions);

  /**
   * Submits a task to this queue in the provided Transaction.
   * A task is added if and only if the transaction is applied successfully.
   * @param txn an enclosing {@link Transaction} or null, if not null a task cannot be named.
   * @param taskOptions The definition of the task.
   * @return A {@link TaskHandle}.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TaskAlreadyExistsException if a task with the same name was previously created.
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   * @throws UnsupportedTranslationException If chosen character encoding is unsupported.
   * @throws InvalidQueueModeException task method is {@link TaskOptions.Method#PULL} and queue is
   * push queue or vice versa.
   */
  TaskHandle add(Transaction txn, TaskOptions taskOptions);

  /**
   * Submits tasks to this queue in the provided Transaction.
   * The tasks are added if and only if the transaction is applied successfully.
   * @param txn an enclosing {@link Transaction} or null, if not null a task cannot be named.
   * @param taskOptions An iterable over task definitions.
   * @return A list containing a {@link TaskHandle} for each added task.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TaskAlreadyExistsException if a task with the same name was previously created.
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   * @throws UnsupportedTranslationException If chosen character encoding is unsupported.
   * @throws InvalidQueueModeException task method is {@link TaskOptions.Method#PULL} and queue is
   * push queue or vice versa.
   */
  List<TaskHandle> add(Transaction txn, Iterable<TaskOptions> taskOptions);

  /**
   * Deletes a task from this {@link Queue}. Task is identified by taskName.
   * @param taskName name of the task to delete.
   * @return True if the task was sucessfully deleted. False if the task was not found or was
   * previously deleted.
   * @throws IllegalArgumentException The provided name is null, empty or doesn't match the expected
   *         pattern.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   */
  boolean deleteTask(String taskName);

  /**
   * Deletes a task from this {@link Queue}. Task is identified by a TaskHandle.
   * @param taskHandle handle of the task to delete.
   * @return True if the task was sucessfully deleted. False if the task was not found or was
   * previously deleted.
   * @throws IllegalArgumentException The provided name is null, empty or doesn't match the expected
   *         pattern.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws QueueNameMismatchException The task handle refers to a different named queue.
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   */
  boolean deleteTask(TaskHandle taskHandle);

  /**
   * Deletes a list of tasks from this {@link Queue}. The tasks are identified
   *        by a list of TaskHandles.
   * @param taskHandles list of handles of tasks to delete.
   * @return List<Booloean> that represents the result of deleting each task in
   *         the same order as the input handles. True if a task was sucessfully deleted.
   *         False if the task was not found or was previously deleted.
   * @throws IllegalArgumentException The provided name is null, empty or doesn't match the expected
   *         pattern.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws QueueNameMismatchException The task handle refers to a different named queue.
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   */
  List<Boolean> deleteTask(List<TaskHandle> taskHandles);

  /**
   * Leases up to {@code countLimit} tasks from this queue for a period specified by
   * {@code lease} and {@code unit}. If fewer tasks than countLimit are available, all available
   * tasks in this {@link Queue} will be returned. The available tasks are those in the queue
   * having the earliest eta such that eta is prior to the time at which the lease is requested.
   * It is guaranteed that the leased tasks will be unavailable for lease to others in the
   * lease period. You must call deleteTask to prevent the task from being leased again after
   * the lease period.. This method supports leasing a maximum of 1000 tasks for no more than one
   * week.
   * @param lease Number of {@code unit}s in the lease period
   * @param unit Time unit of the lease period
   * @param countLimit maximum number of tasks to lease
   * @return A list of {@link TaskHandle} for each leased task.
   * @throws InvalidQueueModeException if the target queue is not in pull mode.
   * @throws IllegalArgumentException if lease < 0, countLimit <= 0 or either is too large.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   */
  List<TaskHandle> leaseTasks(long lease, TimeUnit unit, long countLimit);

  /**
   * Clears all the tasks in this {@link Queue}. This function returns
   * immediately. Some delay may apply on the server before the Queue is
   * actually purged. Tasks being executed at the time the purge call is
   * made will continue executing, other tasks in this Queue will continue
   * being dispatched and executed before the purge call takes effect.
   * @throws IllegalStateException If the Queue does not exist. (see queue.xml)
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   * @throws InternalFailureException
   */
  void purge();
}
