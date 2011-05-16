// Copyright 2009 Google Inc. All Rights Reserved.
package com.google.appengine.tools.development.testing;

import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.dev.LocalTaskQueue;
import com.google.appengine.api.taskqueue.dev.LocalTaskQueueCallback;
import com.google.appengine.tools.development.ApiProxyLocal;

/**
 * Config for accessing the local task queue in tests. Default behavior is to
 * configure the local task queue to not automatically execute any tasks.
 * {@link #tearDown()} wipes out all in-memory state so all queues are empty at
 * the end of every test.
 *
 */
public final class LocalTaskQueueTestConfig implements LocalServiceTestConfig {

  private Boolean disableAutoTaskExecution = true;
  private String queueXmlPath;
  private Class<? extends LocalTaskQueueCallback> callbackClass;
  private boolean shouldCopyApiProxyEnvironment = false;

  /**
   * Disables/enables automatic task execution. If you enable automatic task
   * execution, keep in mind that the default behavior is to hit the url that
   * was provided when the {@link TaskOptions} was constructed. If you do not
   * have a servlet engine running, this will fail. As an alternative to
   * launching a servlet engine, instead consider providing a
   * {@link LocalTaskQueueCallback} via {@link #setCallbackClass(Class)} so that
   * you can assert on the properties of the URLFetchServicePb.URLFetchRequest.
   *
   * @param disableAutoTaskExecution
   * @return {@code this} (for chaining)
   */
  public LocalTaskQueueTestConfig setDisableAutoTaskExecution(boolean disableAutoTaskExecution) {
    this.disableAutoTaskExecution = disableAutoTaskExecution;
    return this;
  }

  /**
   * Overrides the location of queue.xml. Must be a full path, e.g.
   * /usr/local/dev/myapp/test/queue.xml
   *
   * @param queueXmlPath
   * @return {@code this} (for chaining)
   */
  public LocalTaskQueueTestConfig setQueueXmlPath(String queueXmlPath) {
    this.queueXmlPath = queueXmlPath;
    return this;
  }

  /**
   * Overrides the callback implementation used by the local task queue for
   * async task execution.
   *
   * @param callbackClass fully-qualified name of a class with a public, default
   *        constructor that implements {@link LocalTaskQueueCallback}.
   * @return {@code this} (for chaining)
   */
  public LocalTaskQueueTestConfig setCallbackClass(
      Class<? extends LocalTaskQueueCallback> callbackClass) {
    this.callbackClass = callbackClass;
    return this;
  }

  /**
   * Enables copying of the {@code ApiProxy.Environment} to task handler
   * threads. This setting is ignored unless both
   * <ol>
   * <li>a {@link #setCallbackClass(Class) callback} class has been set, and
   * <li>automatic task execution has been
   * {@link #setDisableAutoTaskExecution(boolean) enabled.}
   * </ol>
   * In this case tasks will be handled locally by new threads and it may be
   * useful for those threads to use the same environment data as the main test
   * thread. Properties such as the
   * {@link LocalServiceTestHelper#setEnvAppId(String) appID}, and the user
   * {@link LocalServiceTestHelper#setEnvEmail(String) email} will be copied
   * into the environment of the task threads. Be aware that
   * {@link LocalServiceTestHelper#setEnvAttributes(java.util.Map) attribute
   * map} will be shallow-copied to the task thread environents, so that any
   * mutable objects used as values of the map should be thread safe. If this
   * property is {@code false} then the task handler threads will have an empty
   * {@code ApiProxy.Environment}. This property is {@code false} by default.
   *
   * @param b should the {@code ApiProxy.Environment} be pushed to task handler
   *        threads
   * @return {@code this} (for chaining)
   */
  public LocalTaskQueueTestConfig setShouldCopyApiProxyEnvironment(boolean b) {
    this.shouldCopyApiProxyEnvironment = b;
    return this;
  }

  @Override
  public void setUp() {
    ApiProxyLocal proxy = LocalServiceTestHelper.getApiProxyLocal();
    proxy.setProperty(
        LocalTaskQueue.DISABLE_AUTO_TASK_EXEC_PROP, disableAutoTaskExecution.toString());
    if (queueXmlPath != null) {
      proxy.setProperty(LocalTaskQueue.QUEUE_XML_PATH_PROP, queueXmlPath);
    }
    if (callbackClass != null) {
      String callbackName;
      if (!disableAutoTaskExecution) {
        EnvSettingTaskqueueCallback.setProxyProperties(
            proxy, callbackClass, shouldCopyApiProxyEnvironment);
        callbackName = EnvSettingTaskqueueCallback.class.getName();
      } else {
        callbackName = callbackClass.getName();
      }
      proxy.setProperty(LocalTaskQueue.CALLBACK_CLASS_PROP, callbackName);
    }
  }

  @Override
  public void tearDown() {
    LocalTaskQueue ltq = getLocalTaskQueue();
    for (String queueName : ltq.getQueueStateInfo().keySet()) {
      ltq.flushQueue(queueName);
    }
  }

  public static LocalTaskQueue getLocalTaskQueue() {
    return (LocalTaskQueue) LocalServiceTestHelper.getLocalService(LocalTaskQueue.PACKAGE);
  }

}
