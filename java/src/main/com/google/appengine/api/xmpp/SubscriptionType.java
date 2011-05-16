// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.api.xmpp;

/**
 * Values for the 'type' attribute of presences, taken from RFC3921. These are only types from
 * stanzas dealing with subscriptiosn. Types used for presence information are enumerated in
 * {@link PresenceType} even though they are both communicated via presence stanzas.
 *
 */
public enum SubscriptionType {
  /**
   * Signals that a contact has requested a subscription.
   */
  SUBSCRIBE,

  /**
   * Signals that a contact has accepted a request for subscription.
   */
  SUBSCRIBED,

  /**
   * Signals that a contact is requesting an end to a subscription.
   */
  UNSUBSCRIBE,

  /**
   * Signals that a contact has ended a subscription.
   */
  UNSUBSCRIBED
}
