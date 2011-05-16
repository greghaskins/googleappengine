// Copyright 2007 Google Inc. All rights reserved.

package com.google.appengine.api.urlfetch;

public class URLFetchServiceFactory {
  public static URLFetchService getURLFetchService() {
    return new URLFetchServiceImpl();
  }
}
