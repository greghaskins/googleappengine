// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.api.xmpp;

/**
 * Values for the 'show' sub-stanza of presences, taken from RFC3921.
 */
public enum PresenceShow {
  /**
   * The entity is assumed to be online and available.
   */
  NONE,

  /**
   * The entity or resource is temporarily away.
   */
  AWAY,

  /**
   * The entity or resource is actively interested in chatting.
   */
  CHAT,

  /**
   * The entity or resource is busy (dnd = "Do Not Disturb").
   */
  DND,

  /**
   * The entity or resource is away for an extended period
   * (xa = "eXtended Away").
   */
  XA
}
