// Copyright 2009 Google Inc. All rights reserved.

package com.google.appengine.api.blobstore;

import java.io.Serializable;
import java.util.Date;

/**
 * {@code BlobInfo} contains metadata about a blob. This metadata is gathered by
 * parsing the HTTP headers included in the blob upload. For more information on
 * HTTP file uploads, see <a href="http://www.ietf.org/rfc/rfc1867.txt">
 * RFC 1867</a>.
 *
 */
public class BlobInfo implements Serializable {
  protected final BlobKey blobKey;
  protected final String contentType;
  protected final Date creation;
  protected final String filename;
  protected final long size;

  /**
   * Creates a {@code BlobInfo} by providing the {@link BlobKey} and all
   * associated metadata. This is typically done by the API on the developer's
   * behalf.
   */
  public BlobInfo(BlobKey blobKey, String contentType, Date creation, String filename, long size) {
    if (blobKey == null) {
      throw new NullPointerException("blobKey must not be null");
    }
    if (contentType == null) {
      throw new NullPointerException("contentType must not be null");
    }
    if (creation == null) {
      throw new NullPointerException("creation must not be null");
    }
    if (filename == null) {
      throw new NullPointerException("filename must not be null");
    }

    this.blobKey = blobKey;
    this.contentType = contentType;
    this.creation = creation;
    this.filename = filename;
    this.size = size;
  }

  /**
   * Returns the {@link BlobKey} of the Blob this {@code BlobInfo} describes.
   */
  public BlobKey getBlobKey() {
    return blobKey;
  }

  /**
   * Returns the MIME Content-Type provided in the HTTP header during upload of
   * this Blob.
   */
  public String getContentType() {
    return contentType;
  }

  /**
   * Returns the time and date the blob was upload.
   */
  public Date getCreation() {
    return creation;
  }

  /**
   * Returns the file included in the Content-Disposition HTTP header during
   * upload of this Blob.
   */
  public String getFilename() {
    return filename;
  }

  /**
   * Returns the size in bytes of this Blob.
   */
  public long getSize() {
    return size;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof BlobInfo) {
      BlobInfo bi = (BlobInfo) obj;
      return blobKey.equals(bi.blobKey) &&
          contentType.equals(bi.contentType) &&
          creation.equals(bi.creation) &&
          filename.equals(bi.filename) &&
          size == bi.size;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 17;
    hash = hash * 37 + blobKey.hashCode();
    hash = hash * 37 + contentType.hashCode();
    hash = hash * 37 + filename.hashCode();
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("<BlobInfo: ");
    builder.append(blobKey);
    builder.append(", contentType = ");
    builder.append(contentType);
    builder.append(", creation = ");
    builder.append(creation.toString());
    builder.append(", filename = ");
    builder.append(filename);
    builder.append(", size = ");
    builder.append(Long.toString(size));
    builder.append(">");
    return builder.toString();
  }
}
