// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.appengine.api.NamespaceManager;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.AppEngineWebXml;

import java.util.Collections;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * {@code LocalEnvironment} is a simple {@link ApiProxy.Environment} that reads
 * application-specific details (e.g. application identifer) directly from the
 * custom deployment descriptor.
 *
 */
abstract public class LocalEnvironment implements ApiProxy.Environment {
  private static final Logger logger = Logger.getLogger(LocalEnvironment.class.getName());
  private static final String APPS_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".appsNamespace";
  /**
   * {@code ApiProxy.Environment} instances used with {@code
   * ApiProxyLocalFactory} should have a entry in the map returned by
   * {@code getAttributes()} with this key, and a {@link
   * java.util.concurrent.Semaphore} as the value.  This is used
   * internally to track asynchronous API calls.
   */
  static final String API_CALL_SEMAPHORE =
      "com.google.appengine.tools.development.api_call_semaphore";

  /**
   * The name of an {@link #getAttributes() attribute} that contains a
   * (String) unique identifier for the curent request to the
   * Dev App Server.
   */
  public static final String REQUEST_ID = "com.google.appengine.tools.development.request_id";

  /**
   * The name of an {@link #getAttributes() attribute} that contains a {@code
   * Set<RequestEndListener>}. The set of {@link RequestEndListener
   * RequestEndListeners} is populated by from within the service calls. The
   * listeners are invoked at the end of a user request.
   */
  public static final String REQUEST_END_LISTENERS =
      "com.google.appengine.tools.development.request_end_listeners";

  private final AppEngineWebXml appEngineWebXml;

  protected final ConcurrentMap<String, Object> attributes =
      new ConcurrentHashMap<String, Object>();

  protected LocalEnvironment(AppEngineWebXml appEngineWebXml) {
    this.appEngineWebXml = appEngineWebXml;
    attributes.put(REQUEST_ID, generateRequestId());
    attributes.put(REQUEST_END_LISTENERS,
        Collections.newSetFromMap(new ConcurrentHashMap<RequestEndListener, Boolean>(10)));
  }

  private static final String REQUEST_ID_PREFIX = "" + System.currentTimeMillis();
  private static AtomicInteger requestID = new AtomicInteger();
  private String generateRequestId(){
    return REQUEST_ID_PREFIX + "," + requestID.getAndIncrement();
  }

  public String getAppId() {
    return appEngineWebXml.getAppId();
  }

  public String getVersionId() {
    return appEngineWebXml.getMajorVersionId() + ".1";
  }

  public String getAuthDomain() {
    return "gmail.com";
  }

  @Override
  @Deprecated
  public final String getRequestNamespace() {
    String appsNamespace = (String) getAttributes().get(APPS_NAMESPACE_KEY);
    return appsNamespace == null ? "" : appsNamespace;
  }

  public ConcurrentMap<String, Object> getAttributes() {
    return attributes;
  }
}
