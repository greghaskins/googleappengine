// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.api.xmpp;

/**
 * Constructs an instance of the XMPP service.
 * 
 * @author kushal@google.com (Kushal Dave)
 */
public class XMPPServiceFactory {
  
  public static XMPPService getXMPPService() {
    return new XMPPServiceImpl();
  }
  
}
