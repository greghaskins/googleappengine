// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.appengine.api.appidentity;

import com.google.appengine.api.appidentity.AppIdentityServicePb.AppIdentityServiceError;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetAccessTokenRequest;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetAccessTokenResponse;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetPublicCertificateForAppRequest;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetPublicCertificateForAppResponse;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetServiceAccountNameRequest;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetServiceAccountNameResponse;
import com.google.appengine.api.appidentity.AppIdentityServicePb.SignForAppRequest;
import com.google.appengine.api.appidentity.AppIdentityServicePb.SignForAppResponse;
import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Date;
import java.util.List;

/**
 * Implementation of the AppIdentityService interface.
 *
 */
class AppIdentityServiceImpl implements AppIdentityService {

  public static final String PACKAGE_NAME = "app_identity_service";

  public static final String SIGN_FOR_APP_METHOD_NAME = "SignForApp";

  public static final String GET_SERVICE_ACCOUNT_NAME_METHOD_NAME = "GetServiceAccountName";

  public static final String GET_CERTS_METHOD_NAME = "GetPublicCertificatesForApp";

  public static final String GET_ACCESS_TOKEN_METHOD_NAME = "GetAccessToken";

  public static final String MEMCACHE_NAMESPACE = "_ah_";

  public static final String MEMCACHE_KEY_PREFIX = "_ah_app_identity_";

  private void handleApplicationError(ApiProxy.ApplicationException e) {
    switch (AppIdentityServiceError.ErrorCode.valueOf(e.getApplicationError())) {
      case BLOB_TOO_LARGE:
        throw new AppIdentityServiceFailureException(e.getErrorDetail());
      case NOT_A_VALID_APP:
        throw new AppIdentityServiceFailureException(e.getErrorDetail());
      case DEADLINE_EXCEEDED:
        throw new AppIdentityServiceFailureException(e.getErrorDetail());
      case UNKNOWN_ERROR:
        throw new AppIdentityServiceFailureException(e.getErrorDetail());
      case UNKNOWN_SCOPE:
        throw new AppIdentityServiceFailureException(e.getErrorDetail());
      default:
        throw new AppIdentityServiceFailureException(e.getErrorDetail());
    }
  }

  @Override
  public List<PublicCertificate> getPublicCertificatesForApp() {
    GetPublicCertificateForAppRequest.Builder requestBuilder =
      GetPublicCertificateForAppRequest.newBuilder();
    GetPublicCertificateForAppResponse.Builder responseBuilder =
      GetPublicCertificateForAppResponse.newBuilder();

    try {
      responseBuilder.mergeFrom(
          ApiProxy.makeSyncCall(
              PACKAGE_NAME, GET_CERTS_METHOD_NAME, requestBuilder.build().toByteArray()));
    } catch (ApiProxy.ApplicationException e) {
      handleApplicationError(e);
    } catch (InvalidProtocolBufferException e) {
      throw new AppIdentityServiceFailureException(e.getMessage());
    }
    GetPublicCertificateForAppResponse response = responseBuilder.build();

    List<PublicCertificate> certs = Lists.newArrayList();
    for (AppIdentityServicePb.PublicCertificate cert : response.getPublicCertificateListList()) {
      certs.add(new PublicCertificate(cert.getKeyName(), cert.getX509CertificatePem()));
    }
    return certs;
  }

  @Override
  public SigningResult signForApp(byte[] signBlob) {
    SignForAppRequest.Builder requestBuilder = SignForAppRequest.newBuilder();
    requestBuilder.setBytesToSign(ByteString.copyFrom(signBlob));
    SignForAppResponse.Builder responseBuilder = SignForAppResponse.newBuilder();
    try {
      responseBuilder.mergeFrom(
          ApiProxy.makeSyncCall(
              PACKAGE_NAME, SIGN_FOR_APP_METHOD_NAME, requestBuilder.build().toByteArray()));
    } catch (ApiProxy.ApplicationException e) {
      handleApplicationError(e);
    } catch (InvalidProtocolBufferException e) {
      throw new AppIdentityServiceFailureException(e.getMessage());
    }

    SignForAppResponse response = responseBuilder.build();
    return new SigningResult(response.getKeyName(), response.getSignatureBytes().toByteArray());
  }

  @Override
  public String getServiceAccountName() {
    GetServiceAccountNameRequest.Builder requestBuilder = GetServiceAccountNameRequest.newBuilder();
    GetServiceAccountNameResponse.Builder responseBuilder =
      GetServiceAccountNameResponse.newBuilder();
    try {
      responseBuilder.mergeFrom(
          ApiProxy.makeSyncCall(
              PACKAGE_NAME, GET_SERVICE_ACCOUNT_NAME_METHOD_NAME,
              requestBuilder.build().toByteArray()));
    } catch (ApiProxy.ApplicationException e) {
      handleApplicationError(e);
    } catch (InvalidProtocolBufferException e) {
      throw new AppIdentityServiceFailureException(e.getMessage());
    }

    GetServiceAccountNameResponse response = responseBuilder.build();
    return response.getServiceAccountName();
  }

  @Override
  public GetAccessTokenResult getAccessTokenUncached(Iterable<String> scopes) {
    GetAccessTokenRequest.Builder requestBuilder = GetAccessTokenRequest.newBuilder();
    for (String scope : scopes) {
      requestBuilder.addScope(scope);
    }
    if (requestBuilder.getScopeCount() == 0) {
      throw new AppIdentityServiceFailureException("No scopes specified.");
    }
    GetAccessTokenResponse.Builder responseBuilder = GetAccessTokenResponse.newBuilder();
    try {
      responseBuilder.mergeFrom(
          ApiProxy.makeSyncCall(
              PACKAGE_NAME, GET_ACCESS_TOKEN_METHOD_NAME,
              requestBuilder.build().toByteArray()));
    } catch (ApiProxy.ApplicationException e) {
      handleApplicationError(e);
    } catch (InvalidProtocolBufferException e) {
      throw new AppIdentityServiceFailureException(e.getMessage());
    }

    GetAccessTokenResponse response = responseBuilder.build();
    return new GetAccessTokenResult(response.getAccessToken(),
                                    new Date(response.getExpirationTime() * 1000));
  }

  private String memcacheKeyForScopes(Iterable<String> scopes) {
    StringBuilder builder = new StringBuilder();
    builder.append(MEMCACHE_KEY_PREFIX);
    builder.append("[");
    boolean first = true;
    for (String scope : scopes) {
      if (first) {
        first = false;
      } else {
        builder.append(",");
      }
      builder.append("'");
      builder.append(scope);
      builder.append("'");
    }
    builder.append("]");
    return builder.toString();
  }

  @Override
  public GetAccessTokenResult getAccessToken(Iterable<String> scopes) {
    MemcacheService memcache = MemcacheServiceFactory.getMemcacheService(MEMCACHE_NAMESPACE);
    String memcacheKey = memcacheKeyForScopes(scopes);
    GetAccessTokenResult result;
    Object memcacheResult = memcache.get(memcacheKey);
    if (memcacheResult != null) {
      result = (GetAccessTokenResult) memcacheResult;
    } else {
      result = getAccessTokenUncached(scopes);
      Date memcacheExpiration = new Date(result.getExpirationTime().getTime() - 300000);
      memcache.put(memcacheKey, result, Expiration.onDate(memcacheExpiration));
    }
    return result;
  }

}
