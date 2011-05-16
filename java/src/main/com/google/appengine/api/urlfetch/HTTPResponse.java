// Copyright 2007 Google Inc. All rights reserved.

package com.google.appengine.api.urlfetch;

import java.net.URL;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code HTTPResponse} encapsulates the results of a {@code
 * HTTPRequest} made via the {@code URLFetchService}.
 *
 */
public class HTTPResponse implements Serializable {
  static final long serialVersionUID = -4270789523885950851L;

  private final int responseCode;
  private final List<HTTPHeader> headers;
  private byte[] content;
  private URL finalUrl;

  HTTPResponse(int responseCode) {
    this.responseCode = responseCode;
    this.headers = new ArrayList<HTTPHeader>();
    this.finalUrl = null;
  }

  /**
   * Returns the HTTP response code from the request (e.g. 200, 500,
   * etc.).
   */
  public int getResponseCode() {
    return responseCode;
  }

  /**
   * Returns the content of the request, or null if there is no
   * content present (e.g. in a HEAD request).
   */
  public byte[] getContent() {
    return content;
  }

  /**
   * Returns the final URL the content came from if redirects were followed
   * automatically in the request, if different than the input URL; otherwise
   * this will be null.
   */
  public URL getFinalUrl() {
    return finalUrl;
  }

  /**
   * Returns a {@code List} of HTTP response headers that were
   * returned by the remote server. Multi-valued headers are
   * represented as a single {@code HTTPHeader} with comma-separated
   * values.
   */
  public List<HTTPHeader> getHeaders() {
    return Collections.unmodifiableList(headers);
  }

  void addHeader(String name, String value) {
    headers.add(new HTTPHeader(name, value));
  }

  void setContent(byte[] content) {
    this.content = content;
  }

  void setFinalUrl(URL finalUrl) {
    this.finalUrl = finalUrl;
  }
}
