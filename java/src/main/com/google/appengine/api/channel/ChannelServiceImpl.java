// Copyright 2010 Google Inc. All rights reserved.

package com.google.appengine.api.channel;

import com.google.appengine.api.channel.ChannelServicePb.ChannelServiceError.ErrorCode;
import com.google.appengine.api.channel.ChannelServicePb.CreateChannelRequest;
import com.google.appengine.api.channel.ChannelServicePb.CreateChannelResponse;
import com.google.appengine.api.channel.ChannelServicePb.SendMessageRequest;
import com.google.apphosting.api.ApiProxy;

import javax.servlet.http.HttpServletRequest;

/**
 * An implementation of the {@link ChannelService}.
 *
 */
class ChannelServiceImpl implements ChannelService {

  public static final String PACKAGE = "channel";
  public static final String CLIENT_ID_PARAM = "key";
  public static final String MESSAGE_PARAM = "msg";

  public static final int MAXIMUM_CLIENT_ID_CHARS = 64;
  public static final int MAXIMUM_MESSAGE_CHARS = 32767;

  @Override
  public String createChannel(String clientId) {
    CreateChannelRequest request = new CreateChannelRequest()
        .setApplicationKey(clientId);

    if (request.getApplicationKeyAsBytes().length > MAXIMUM_CLIENT_ID_CHARS) {
      throw getExceptionForPrecondition(ErrorCode.INVALID_CHANNEL_KEY);
    }

    byte[] responseBytes;
    try {
      responseBytes = ApiProxy.makeSyncCall(PACKAGE, "CreateChannel", request.toByteArray());
    } catch (ApiProxy.ApplicationException e) {
      throw getExceptionForError(ErrorCode.valueOf(e.getApplicationError()), e);
    }

    CreateChannelResponse response = new CreateChannelResponse();
    response.mergeFrom(responseBytes);
    return response.getClientId();
  }

  @Override
  public void sendMessage(ChannelMessage message) {
    SendMessageRequest request = new SendMessageRequest()
        .setApplicationKey(message.getClientId())
        .setMessage(message.getMessage());

    if (request.getApplicationKeyAsBytes().length > MAXIMUM_CLIENT_ID_CHARS) {
      throw getExceptionForPrecondition(ErrorCode.INVALID_CHANNEL_KEY);
    }

    if (request.getMessageAsBytes().length > MAXIMUM_MESSAGE_CHARS) {
      throw getExceptionForPrecondition(ErrorCode.BAD_MESSAGE);
    }

    byte[] responseBytes;
    try {
      responseBytes = ApiProxy.makeSyncCall(PACKAGE, "SendChannelMessage", request.toByteArray());
    } catch (ApiProxy.ApplicationException e) {
      throw getExceptionForError(ErrorCode.valueOf(e.getApplicationError()), e);
    }
  }

  @Override
  public ChannelMessage parseMessage(HttpServletRequest request) {
    String clientId = request.getParameter(CLIENT_ID_PARAM);
    String message = request.getParameter(MESSAGE_PARAM);

    if (clientId == null) {
      throw new IllegalStateException("Client id parameter not found in HTTP request.");
    }

    if (message == null) {
      throw new IllegalStateException("Message parameter not found in HTTP request.");
    }

    return new ChannelMessage(clientId, message);
  }

  private RuntimeException getExceptionForPrecondition(ErrorCode errorCode) {
    String description;
    switch (errorCode) {
      case INVALID_CHANNEL_KEY:
        description =
            "Invalid client ID. The clientid must be fewer than 64 bytes when encoded to UTF-8.";
        break;
      case BAD_MESSAGE:
        description = "The message must be fewer than 32767 bytes when encoded to UTF-8.";
        break;
      default:
        description = "An unexpected error occurred.";
        break;
    }

    return new IllegalArgumentException(description);
  }

  private RuntimeException getExceptionForError(ErrorCode errorCode, Exception e) {
    String description;
    switch (errorCode) {
      case INTERNAL_ERROR:
        return new ChannelFailureException("An internal channel error occured.");
      default:
        return new ChannelFailureException("An unexpected error occurred.", e);
    }
  }
}
