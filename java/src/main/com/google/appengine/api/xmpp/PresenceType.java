// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.api.xmpp;

/**
 * Values for the 'type' attribute of presences, taken from RFC3921. These are only types from
 * stanzas dealing with presence. Types used for subscriptions are enumerated in
 * {@link SubscriptionType} even though they are both communicated via presence stanzas.
 *
 */
public enum PresenceType {
  /**
   * Signals that an entity is online and available for communication.
   */
  AVAILABLE,

  /**
   * Signals that an entity is no longer available for communication.
   */
  UNAVAILABLE,

  /**
   * A request for an entity's current presence.
   */
  PROBE
}
