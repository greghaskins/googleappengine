// Copyright 2010 Google Inc. All rights reserved.

package com.google.appengine.api.oauth;

import com.google.appengine.api.users.User;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.UserServicePb.CheckOAuthSignatureRequest;
import com.google.apphosting.api.UserServicePb.CheckOAuthSignatureResponse;
import com.google.apphosting.api.UserServicePb.GetOAuthUserRequest;
import com.google.apphosting.api.UserServicePb.GetOAuthUserResponse;
import com.google.apphosting.api.UserServicePb.UserServiceError;
import com.google.io.protocol.ProtocolMessage;

/**
 * Implementation of {@link OAuthService}.
 *
 */
final class OAuthServiceImpl implements OAuthService {
  static final String GET_OAUTH_USER_RESPONSE_KEY =
      "com.google.appengine.api.oauth.OAuthService.get_oauth_user_response";

  private static final String PACKAGE = "user";
  private static final String CHECK_SIGNATURE_METHOD = "CheckOAuthSignature";
  private static final String GET_OAUTH_USER_METHOD = "GetOAuthUser";

  public User getCurrentUser() throws OAuthRequestException {
    GetOAuthUserResponse response = getGetOAuthUserResponse();
    return new User(response.getEmail(), response.getAuthDomain(),
        response.getUserId());
  }

  public boolean isUserAdmin() throws OAuthRequestException {
    GetOAuthUserResponse response = getGetOAuthUserResponse();
    return response.isIsAdmin();
  }

  public String getOAuthConsumerKey() throws OAuthRequestException {
    CheckOAuthSignatureRequest request = new CheckOAuthSignatureRequest();
    byte[] responseBytes = makeSyncCall(CHECK_SIGNATURE_METHOD, request);
    CheckOAuthSignatureResponse response = new CheckOAuthSignatureResponse();
    response.mergeFrom(responseBytes);
    return response.getOauthConsumerKey();
  }

  private GetOAuthUserResponse getGetOAuthUserResponse()
      throws OAuthRequestException {
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
    GetOAuthUserResponse response = (GetOAuthUserResponse)
        environment.getAttributes().get(GET_OAUTH_USER_RESPONSE_KEY);
    if (response == null) {
      GetOAuthUserRequest request = new GetOAuthUserRequest();
      byte[] responseBytes = makeSyncCall(GET_OAUTH_USER_METHOD, request);
      response = new GetOAuthUserResponse();
      response.mergeFrom(responseBytes);
      environment.getAttributes().put(GET_OAUTH_USER_RESPONSE_KEY, response);
    }
    return response;
  }

  private byte[] makeSyncCall(String methodName, ProtocolMessage request)
      throws OAuthRequestException {
    byte[] responseBytes;
    try {
      byte[] requestBytes = request.toByteArray();
      responseBytes = ApiProxy.makeSyncCall(PACKAGE, methodName, requestBytes);
    } catch (ApiProxy.ApplicationException ex) {
      UserServiceError.ErrorCode errorCode =
          UserServiceError.ErrorCode.valueOf(ex.getApplicationError());
      switch (errorCode) {
        case NOT_ALLOWED:
        case OAUTH_INVALID_REQUEST:
          throw new InvalidOAuthParametersException(ex.getErrorDetail());
        case OAUTH_INVALID_TOKEN:
          throw new InvalidOAuthTokenException(ex.getErrorDetail());
        case OAUTH_ERROR:
        default:
          throw new OAuthServiceFailureException(ex.getErrorDetail());
      }
    }

    return responseBytes;
  }
}
