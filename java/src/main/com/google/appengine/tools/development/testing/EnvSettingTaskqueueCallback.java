// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development.testing;

import com.google.appengine.api.taskqueue.dev.LocalTaskQueueCallback;
import com.google.appengine.api.urlfetch.URLFetchServicePb;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.apphosting.api.ApiProxy;

import java.util.Map;

/**
 * An implementation of {@code LocalTaskQueueCallback} that wraps a delegate and
 * invokes {@link
 * ApiProxy#setEnvironmentForCurrentThread(com.google.apphosting.api.ApiProxy.Environment)}
 * prior to invoking the delegate.
 * <p>
 * There are two types of threads that may interact with this class. Class 1
 * consists of threads used to initialize data relevent to this class. These are
 * the main thread of a unit test, which will invoke
 * {@link #setProxyProperties(ApiProxyLocal, Class, boolean)} and the threads
 * started by {@code ApiProxyLocalImpl.makeAsyncCall()} which will invoke
 * {@link #initialize(Map)}.
 * <p>
 * Class 2 consists of automatic task hanlding threads which will invoke {@link
 * #execute(com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest)}.
 * <p>
 * The goal of this class is to be able to get data about an
 * {@link ApiProxy.Environment} from class 1 threads to class 2 threads. We want
 * the class 2 threads to have an {@code Environment} so that they can interact
 * with App Engine APIs such as the datastore.
 *
 */
class EnvSettingTaskqueueCallback implements LocalTaskQueueCallback {

  /**
   * The name of a property used in the
   * {@link ApiProxyLocal#setProperty(String, String) property map} of {@code
   * ApiProxyLocal} for storing the name of the delegate class.
   */
  private static final String DELEGATE_CLASS_PROP =
      "com.google.appengine.tools.development.testing.EnvSettingTaskqueueCallback.delegateClass";

  /**
   * The name of a property used in the
   * {@link ApiProxyLocal#setProperty(String, String) property map} of {@code
   * ApiProxyLocal} for storing a boolean specifying whether or not to copy the
   * environment to the task threads.
   */
  private static final String COPY_ENVIRONMENT_PROP =
      "com.google.appengine.tools.development.testing.EnvSettingTaskqueueCallback.copyEnvironment";

  /**
   * A helper method invoked from {@link LocalTaskQueueTestConfig} which sets
   * the above two properties.
   *
   * @param proxy The instance of {@code ApiProxyLocal} in which the properties
   *        should be set.
   * @param delegateClass the name of the delegate class.
   * @param shouldCopyApiProxyEnvironment should we copy the {@code Environment}
   *        to the task threads.
   */
  static void setProxyProperties(ApiProxyLocal proxy,
      Class<? extends LocalTaskQueueCallback> delegateClass,
      boolean shouldCopyApiProxyEnvironment) {
    proxy.setProperty(DELEGATE_CLASS_PROP, delegateClass.getName());
    proxy.setProperty(COPY_ENVIRONMENT_PROP, Boolean.toString(shouldCopyApiProxyEnvironment));
  }

  private LocalTaskQueueCallback delegate;
  private boolean shouldCopyEnvironment;
  private ApiProxy.Environment environmentFromInitializingThread;

  /**
   * This is invoked during initialization of the {@code LocalTaskQueue}
   * service. The thread that invokes this has access to the {@code
   * ApiProxy.Environment} that was set by the testing thread. We retrieve the
   * two properties that were set above.
   */
  @Override
  public void initialize(Map<String, String> properties) {
    String delegateClassName = properties.get(DELEGATE_CLASS_PROP);
    try {
      delegate = (LocalTaskQueueCallback) Class.forName(delegateClassName).newInstance();
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    shouldCopyEnvironment = Boolean.parseBoolean(properties.get(COPY_ENVIRONMENT_PROP));
    if (shouldCopyEnvironment) {
      environmentFromInitializingThread = ApiProxy.getCurrentEnvironment();
      if (null == environmentFromInitializingThread) {
        throw new RuntimeException(Thread.currentThread().getName());
      }
    }
  }

  /**
   * This method is invoked from the task execution threads.
   */
  @Override
  public int execute(URLFetchServicePb.URLFetchRequest req) {
    ApiProxy.setEnvironmentForCurrentThread(buildNewEnvironment());
    return delegate.execute(req);
  }

  private ApiProxy.Environment buildNewEnvironment() {
    if (shouldCopyEnvironment) {
      return LocalServiceTestHelper.copyEnvironment(environmentFromInitializingThread);
    }
    return LocalServiceTestHelper.newDefaultTestEnvironment();
  }

}
