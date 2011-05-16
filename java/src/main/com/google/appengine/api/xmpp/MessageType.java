// Copyright 2008 Google Inc. All rights reserved.

package com.google.appengine.api.xmpp;

/**
 * Types of messages, from RFC3921.
 *
 * @author kushal@google.com (Kushal Dave)
 */
public enum MessageType {
  CHAT,
  ERROR,
  GROUPCHAT,
  HEADLINE,
  NORMAL;

  String getInternalName() {
    return name().toLowerCase();
  }
}
