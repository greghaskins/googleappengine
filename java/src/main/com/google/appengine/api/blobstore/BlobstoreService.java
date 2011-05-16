// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.api.blobstore;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * {@code BlobstoreService} allows you to manage the creation and
 * serving of large, immutable blobs to users.
 *
 */
public interface BlobstoreService {
  public static final int MAX_BLOB_FETCH_SIZE = (1 << 20) - (1 << 15);

  /**
   * Create an absolute URL that can be used by a user to
   * asynchronously upload a large blob.  Upon completion of the
   * upload, a callback is made to the specified URL.
   *
   * @param successPath A relative URL which will be invoked
   * after the user successfully uploads a blob.
   *
   * @throws IllegalArgumentException If successPath was not valid.
   * @throws BlobstoreFailureException If an error occurred while
   * communicating with the blobstore.
   */
  String createUploadUrl(String successPath);

  /**
   * Arrange for the specified blob to be served as the response
   * content for the current request.  {@code response} should be
   * uncommitted before invoking this method, and should be assumed to
   * be committed after invoking it.  Any content written before
   * calling this method will be ignored.  You may, however, append
   * custom headers before or after calling this method.
   *
   * <p>Range header will be automatically translated from the Content-Range
   * header in the response.
   *
   * @param blobKey Blob-key to serve in response.
   * @param response HTTP response object.
   *
   * @throws IOException If an I/O error occurred.
   * @throws IllegalStateException If {@code response} was already committed.
   */
  void serve(BlobKey blobKey, HttpServletResponse response) throws IOException;

  /**
   * Arrange for the specified blob to be served as the response
   * content for the current request.  {@code response} should be
   * uncommitted before invoking this method, and should be assumed to
   * be committed after invoking it.  Any content written before
   * calling this method will be ignored.  You may, however, append
   * custom headers before or after calling this method.
   *
   * <p>This method will set the App Engine blob range header to serve a
   * byte range of that blob.
   *
   * @param blobKey Blob-key to serve in response.
   * @param byteRange Byte range to serve in response.
   * @param response HTTP response object.
   *
   * @throws IOException If an I/O error occurred.
   * @throws IllegalStateException If {@code response} was already committed.
   */
  void serve(BlobKey blobKey, ByteRange byteRange, HttpServletResponse response)
      throws IOException;

  /**
   * Arrange for the specified blob to be served as the response
   * content for the current request.  {@code response} should be
   * uncommitted before invoking this method, and should be assumed to
   * be committed after invoking it.  Any content written before
   * calling this method will be ignored.  You may, however, append
   * custom headers before or after calling this method.
   *
   * <p>This method will set the App Engine blob range header to the content
   * specified.
   *
   * @param blobKey Blob-key to serve in response.
   * @param rangeHeader Content for range header to serve.
   * @param response HTTP response object.
   *
   * @throws IOException If an I/O error occurred.
   * @throws IllegalStateException If {@code response} was already committed.
   */
  void serve(BlobKey blobKey, String rangeHeader, HttpServletResponse response)
      throws IOException;

  /**
   * Get byte range from the request.
   *
   * @param request HTTP request object.
   *
   * @return Byte range as parsed from the HTTP range header.  null if there is no header.
   *
   * @throws RangeFormatException Unable to parse header because of invalid format.
   * @throws UnsupportedRangeFormatException Header is a valid HTTP range header, the specific
   * form is not supported by app engine.  This includes unit types other than "bytes" and multiple
   * ranges.
   */
  ByteRange getByteRange(HttpServletRequest request);

  /**
   * Permanently deletes the specified blobs.  Deleting unknown blobs is a
   * no-op.
   *
   * @throws BlobstoreFailureException If an error occurred while
   * communicating with the blobstore.
   */
  void delete(BlobKey... blobKeys);

  /**
   * Returns the {@link BlobKey} for any files that were uploaded.
   * This method should only be called from within a request served by
   * the destination of a {@code createUploadUrl} call.
   *
   * @throws IllegalStateException If not called from a blob upload
   * callback request.
   */
  Map<String, BlobKey> getUploadedBlobs(HttpServletRequest request);

  /**
   * Get fragment from specified blob.
   *
   * @param blobKey Blob-key from which to fetch data.
   * @param startIndex Start index of data to fetch.
   * @param endIndex End index (inclusive) of data to fetch.
   *
   * @throws IllegalArgumentException If blob not found, indexes are negative,
   * indexes are inverted or fetch size is too large.
   * @throws SecurityException If the application does not have acces to the blob.
   * @throws BlobstoreFailureException If an error occurred while communicating
   * with the blobstore.
   */
  byte[] fetchData(BlobKey blobKey, long startIndex, long endIndex);
}
