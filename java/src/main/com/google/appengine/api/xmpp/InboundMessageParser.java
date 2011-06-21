// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.api.xmpp;

import com.google.appengine.api.utils.HttpRequestParser;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.mail.BodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.MessagingException;

/**
 * {@code InboundMessageParser} encapsulates the use of the {@code
 * javax.mail} package to parse an incoming {@code
 * multipart/form-data} HTTP request and to convert it to a {@link
 * Message} object.
 *
 */
class InboundMessageParser extends HttpRequestParser {
  static Message parseMessage(HttpServletRequest request) throws IOException {
    try {
      MimeMultipart multipart = parseMultipartRequest(request);

      MessageBuilder builder = new MessageBuilder();
      builder.withMessageType(MessageType.CHAT);

      int parts = multipart.getCount();
      for (int i = 0; i < parts; i++) {
        BodyPart part = multipart.getBodyPart(i);
        String fieldName = getFieldName(part);
        if ("from".equals(fieldName)) {
          builder.withFromJid(new JID(getTextContent(part)));
        } else if ("to".equals(fieldName)) {
          builder.withRecipientJids(new JID(getTextContent(part)));
        } else if ("body".equals(fieldName)) {
          builder.withBody(getTextContent(part));
        } else if ("stanza".equals(fieldName)) {
          builder.withStanza(getTextContent(part));
        }
      }

      return builder.build();
    } catch (MessagingException ex) {
      IOException ex2 = new IOException("Could not parse incoming request.");
      ex2.initCause(ex);
      throw ex2;
    }
  }
}
