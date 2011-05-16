// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.api.xmpp;

/**
 * Represents presence information returned by the server.
 *
 * @see PresenceBuilder
 *
 * @author kushal@google.com (Kushal Dave)
 */
public final class Presence {

  private final boolean isAvailable;
  private final PresenceType presenceType;
  private final PresenceShow presenceShow;
  private final String status;
  private final JID fromJid;
  private final JID toJid;
  private final String stanza;

  Presence(boolean isAvailable, JID toJid, JID fromJid) {
    this.isAvailable = isAvailable;
    this.presenceType = PresenceType.AVAILABLE;
    this.presenceShow = null;
    this.fromJid = fromJid;
    this.toJid = toJid;
    stanza = null;
    status = null;
  }

  Presence(PresenceType type, PresenceShow show, String status,
                  JID toJid, JID fromJid, String stanza) {
    this.presenceType = type;
    this.isAvailable = type != PresenceType.UNAVAILABLE;
    this.presenceShow = show;
    this.status = status;
    this.toJid = toJid;
    this.fromJid = fromJid;
    this.stanza = stanza;
  }

  public boolean isAvailable() {
    return isAvailable;
  }

  public PresenceType getPresenceType() {
    return presenceType;
  }

 public PresenceShow getPresenceShow() {
    return presenceShow;
  }

  public String getStatus() {
    return status;
  }

  public JID getFromJid() {
    return fromJid;
  }

  public JID getToJid() {
    return toJid;
  }

  public String getStanza() {
    return stanza;
  }
}
