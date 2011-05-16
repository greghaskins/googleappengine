// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.api.blobstore;

import com.google.appengine.api.blobstore.BlobstoreServicePb.BlobstoreServiceError;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateUploadURLRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateUploadURLResponse;
import com.google.appengine.api.blobstore.BlobstoreServicePb.DeleteBlobRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.FetchDataRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.FetchDataResponse;
import com.google.apphosting.api.ApiProxy;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * {@code BlobstoreServiceImpl} is an implementation of {@link
 * BlobstoreService} that makes API calls to {@link ApiProxy}.
 *
 */
class BlobstoreServiceImpl implements BlobstoreService {
  static final String PACKAGE = "blobstore";
  static final String SERVE_HEADER = "X-AppEngine-BlobKey";
  static final String UPLOADED_BLOBKEY_ATTR = "com.google.appengine.api.blobstore.upload.blobkeys";
  static final String BLOB_RANGE_HEADER = "X-AppEngine-BlobRange";

  public String createUploadUrl(String successPath) {
    if (successPath == null) {
      throw new NullPointerException("Success path must not be null.");
    }

    CreateUploadURLRequest request = new CreateUploadURLRequest();
    request.setSuccessPath(successPath);

    byte[] responseBytes;
    try {
      responseBytes = ApiProxy.makeSyncCall("blobstore", "CreateUploadURL", request.toByteArray());
    } catch (ApiProxy.ApplicationException ex) {
      switch (BlobstoreServiceError.ErrorCode.valueOf(ex.getApplicationError())) {
        case URL_TOO_LONG:
          throw new IllegalArgumentException("The resulting URL was too long.");
        case INTERNAL_ERROR:
          throw new BlobstoreFailureException("An internal blobstore error occured.");
        default:
          throw new BlobstoreFailureException("An unexpected error occurred.", ex);
      }
    }

    CreateUploadURLResponse response = new CreateUploadURLResponse();
    response.mergeFrom(responseBytes);
    return response.getUrl();
  }

  public void serve(BlobKey blobKey, HttpServletResponse response) {
    serve(blobKey, (ByteRange) null, response);
  }

  public void serve(BlobKey blobKey, String rangeHeader, HttpServletResponse response) {
    serve(blobKey, ByteRange.parse(rangeHeader), response);
  }

  public void serve(BlobKey blobKey, ByteRange byteRange, HttpServletResponse response) {
    if (response.isCommitted()) {
      throw new IllegalStateException("Response was already committed.");
    }

    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader(SERVE_HEADER, blobKey.getKeyString());
    if (byteRange != null) {
      response.setHeader(BLOB_RANGE_HEADER, byteRange.toString());
    }
  }

  public ByteRange getByteRange(HttpServletRequest request) {
    @SuppressWarnings("unchecked")
    Enumeration<String> rangeHeaders = request.getHeaders("range");
    if (!rangeHeaders.hasMoreElements()) {
      return null;
    }

    String rangeHeader = rangeHeaders.nextElement();
    if (rangeHeaders.hasMoreElements()) {
      throw new UnsupportedRangeFormatException("Cannot accept multiple range headers.");
    }

    return ByteRange.parse(rangeHeader);
  }

  public void delete(BlobKey... blobKeys) {
    DeleteBlobRequest request = new DeleteBlobRequest();
    for (BlobKey blobKey : blobKeys) {
      request.addBlobKey(blobKey.getKeyString());
    }

    if (request.blobKeySize() == 0) {
      return;
    }

    byte[] responseBytes;
    try {
      responseBytes = ApiProxy.makeSyncCall(PACKAGE, "DeleteBlob", request.toByteArray());
    } catch (ApiProxy.ApplicationException ex) {
      switch (BlobstoreServiceError.ErrorCode.valueOf(ex.getApplicationError())) {
        case INTERNAL_ERROR:
          throw new BlobstoreFailureException("An internal blobstore error occured.");
        default:
          throw new BlobstoreFailureException("An unexpected error occurred.", ex);
      }
    }
  }

  public Map<String, BlobKey> getUploadedBlobs(HttpServletRequest request) {
    @SuppressWarnings("unchecked")
    Map<String, String> attributes =
        (Map<String, String>) request.getAttribute(UPLOADED_BLOBKEY_ATTR);
    if (attributes == null) {
      throw new IllegalStateException("Must be called from a blob upload callback request.");
    }
    Map<String, BlobKey> blobKeys = new HashMap<String, BlobKey>(attributes.size());
    for (Map.Entry<String, String> attr : attributes.entrySet()) {
      blobKeys.put(attr.getKey(), new BlobKey(attr.getValue()));
    }
    return blobKeys;
  }

  public byte[] fetchData(BlobKey blobKey, long startIndex, long endIndex) {
    if (startIndex < 0) {
      throw new IllegalArgumentException("Start index must be >= 0.");
    }

    if (endIndex < startIndex) {
      throw new IllegalArgumentException("End index must be >= startIndex.");
    }

    long fetchSize = endIndex - startIndex + 1;
    if (fetchSize > MAX_BLOB_FETCH_SIZE) {
      throw new IllegalArgumentException("Blob fetch size " + fetchSize + " it larger " +
                                         "than maximum size " + MAX_BLOB_FETCH_SIZE + " bytes.");
    }

    FetchDataRequest request = new FetchDataRequest();
    request.setBlobKey(blobKey.getKeyString());
    request.setStartIndex(startIndex);
    request.setEndIndex(endIndex);

    byte[] responseBytes;
    try {
      responseBytes = ApiProxy.makeSyncCall(PACKAGE, "FetchData", request.toByteArray());
    } catch (ApiProxy.ApplicationException ex) {
      switch (BlobstoreServiceError.ErrorCode.valueOf(ex.getApplicationError())) {
        case PERMISSION_DENIED:
          throw new SecurityException("This application does not have access to that blob.");
        case BLOB_NOT_FOUND:
          throw new IllegalArgumentException("Blob not found.");
        case INTERNAL_ERROR:
          throw new BlobstoreFailureException("An internal blobstore error occured.");
        default:
          throw new BlobstoreFailureException("An unexpected error occurred.", ex);
      }
    }

    FetchDataResponse response = new FetchDataResponse();
    response.mergeFrom(responseBytes);
    return response.getDataAsBytes();
  }
}
