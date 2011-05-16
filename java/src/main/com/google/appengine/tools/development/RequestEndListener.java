// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.apphosting.api.ApiProxy.Environment;

/**
 * A {@code RequestEndListener} is a hook that allows
 * local API services to be notified when a request
 * to the Dev App Server has completed.
 * <p>
 * A <i>local API service</i> is a class that implements
 * {@link com.google.appengine.tools.development.LocalRpcService}.
 * As described in that interface, a <i>service call</i> is
 * a method implemented by a local API service
 * that takes a request protocol buffer and returns a
 * response protocol buffer. During a service call
 * a local API service may register a {@code RequestEndListener}
 * using the following code:
 * <blockquote>
 * <pre>
 * Environment environment = ApiProxy.getCurrentEnvironment();
 * Set<RequestEndListener> listenerSet = (Set<RequestEndListener>)
 *   environment.getAttributes().get(LocalEnvironment.REQUEST_END_LISTENERS);
 *  listenerSet.add(listener);
 * </pre>
 * </blockquote>
 * This registers the listener only for the current request.
 *
 */
public interface RequestEndListener {

  /**
   * This method is invoked on all registered {@code RequestEndListeners}
   * when the current request to the Dev App Server has completed.
   * @param environment Environment associated with the request. Among
   * other data contained in this environment is a unique request ID.
   * This may be obtained using the following code:
   *  <blockquote>
   * <pre>
   *  String requestID =
   *  (String) environment.getAttributes().get(LocalEnvironment.REQUEST_ID);
   * </pre>
   * </blockquote>
   */
  void onRequestEnd(Environment environment);
}
