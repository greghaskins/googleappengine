// Copyright 2011 Google Inc. All rights reserved.
package com.google.appengine.api.appidentity;

import java.util.Collection;

/**
 * {@link AppIdentityService} allows you to sign arbitrary string blob using
 * per app private key maintained by App Egnine, and also you can retrieve a
 * list of public certificates which can be used to verify the signature.
 * <p>
 * App Engine is responsible for maintaining per application private key.
 * AppEngine will keep rotating private keys periodically. App Engine never
 * gives these private keys to outside.
 * <p>
 * Since private keys are rotated periodically, getPublicCertificatesForApp
 * could return a list of public certificates, it's caller's responsibility
 * to try these certificates one by one when doing signature verification.
 *
 */
public interface AppIdentityService {

  /**
   * {@link SigningResult} is returned by signForApp, which contains signing key
   * name and signature.
   */
  public static class SigningResult {
    private final String keyName;
    private byte[] signature;

    public SigningResult(String keyName, byte[] signature) {
      this.keyName = keyName;
      this.signature = signature;
    }

    public String getKeyName() {
      return keyName;
    }

    public byte[] getSignature() {
      return signature;
    }
  }

  /**
   * Requests to sign arbitrary string blob using per app private key.
   *
   * @param signBlob string blob.
   * @return a SigningResult object which contains signing key name and
   * signature.
   * @throws AppIdentityServiceFailureException
   */
  SigningResult signForApp(byte[] signBlob);

  /**
   * Retrieves a list of public certificates.
   *
   * @return a list of public certificates.
   * @throws AppIdentityServiceFailureException
   */
  Collection<PublicCertificate> getPublicCertificatesForApp();

  /**
   * Gets service account name of the app.
   *
   * @return service account name of the app.
   */
  String getServiceAccountName();
}
