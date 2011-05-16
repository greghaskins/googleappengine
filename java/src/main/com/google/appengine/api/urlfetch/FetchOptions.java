// Copyright 2008 Google Inc. All Rights Reserved.
package com.google.appengine.api.urlfetch;

import java.io.Serializable;

/**
 * Allows users to customize the behavior of {@link URLFetchService}
 * operations.
 * <p>
 * If {@link #allowTruncate} is called, {@link URLFetchService}
 * will truncate large responses and return them without error.
 * <p>
 * If (@link #disallowTruncate} is called,
 * {@link ResponseTooLargeException} will be thrown if the response is too
 * large.
 * <p>
 * If {@link #followRedirects} is called the {@link URLFetchService}
 * operation will follow redirects.
 * <p>
 * If {@link #doNotFollowRedirects()} is called the {@link URLFetchService}
 * operation will not follow redirects.
 * <p>
 * If {@link #validateCertificate()} is called the {@link URLFetchService}
 * operation will, if using an HTTPS connection, validate the server's
 * SSL certificate using the same CA-based algorithm as a typical browser.
 * HTTP connections are unaffected by this option.
 * <p>
 * If {@link #doNotValidateCertificate()} is called the
 * {@link URLFetchService} will not validate the server's SSL certificate
 * in any fashion.
 * <p>
 * If neither {@link #validateCertificate()} nor
 * {@link #doNotValidateCertificates()} is called, then the underlying
 * {@code URLFetchService} implementation determines what type of validation
 * is performed.
 * <p>
 * Notes on usage:<br>
 * The recommended way to instantiate a {@code FetchOptions} object is to
 * statically import {@link Builder}.* and invoke a static
 * creation method followed by an instance mutator (if needed):
 *
 * <blockquote>
 * <pre>
 * import static com.google.appengine.api.urlfetch.FetchOptions.Builder.*;
 *
 * ...
 * URL url = getURLToFetch();
 * urlFetchService.fetch(new HTTPRequest(url, HTTPMethod.GET,
 *     allowTruncate()));
 *
 * urlFetchService.fetch(new HTTPRequest(url, HTTPMethod.GET,
 *     allowTruncate().doNotFollowRedirects()));
 * </pre>
 * </blockquote>
 *
 */
public final class FetchOptions implements Serializable {
  static final long serialVersionUID = 3904557385413253999L;

  public static final boolean DEFAULT_ALLOW_TRUNCATE = false;
  public static final boolean DEFAULT_FOLLOW_REDIRECTS = true;

  /**
   * The default deadline is defined externally.
   */
  public static final Double DEFAULT_DEADLINE = null;

  private boolean allowTruncate = DEFAULT_ALLOW_TRUNCATE;
  private boolean followRedirects = DEFAULT_FOLLOW_REDIRECTS;
  private Double deadline = DEFAULT_DEADLINE;

  enum CertificateValidationBehavior {
    DEFAULT,
    VALIDATE,
    DO_NOT_VALIDATE
  }

  private CertificateValidationBehavior certificateValidationBehavior =
      CertificateValidationBehavior.DEFAULT;

  private FetchOptions() {
  }

  /**
   * Enables response truncation.  Please read
   * the class javadoc for an explanation of how allowTruncate is used.
   * @return {@code this} (for chaining)
   */
  public FetchOptions allowTruncate() {
    this.allowTruncate = true;
    return this;
  }

  /**
   * Disables response truncation.  Please read
   * the class javadoc for an explanation of how allowTruncate is used.
   * @return {@code this} (for chaining)
   */
  public FetchOptions disallowTruncate() {
    this.allowTruncate = false;
    return this;
  }

  /**
   * Enables following of redirects.  Please read
   * the class javadoc for an explanation of how followRedirects is used.
   * @return {@code this} (for chaining)
   */
  public FetchOptions followRedirects() {
    this.followRedirects = true;
    return this;
  }

  /**
   * Enables certificate validation on HTTPS connections via the normal
   * CA-based mechanism. Please read the class javadoc for an explanation of
   * how this option affects certificate validation behavior.
   * @return {@code this} (for chaining)
   */
  public FetchOptions validateCertificate() {
    this.certificateValidationBehavior = CertificateValidationBehavior.VALIDATE;
    return this;
  }

  /**
   * Disables certificate validation on HTTPS connections. Please read the
   * class javadoc for an explanation of how this option affects certificate
   * validation behavior.
   * @return {@code this} (for chaining)
   */
  public FetchOptions doNotValidateCertificate() {
    this.certificateValidationBehavior = CertificateValidationBehavior.DO_NOT_VALIDATE;
    return this;
  }

  /**
   * Sets the deadline, in seconds, for the fetch request.
   * @throws IllegalArgumentException if deadline is not positive
   * @return {@code this} (for chaining)
   */
  public FetchOptions setDeadline(Double deadline) {
    if (deadline != null && deadline <= 0.0) {
      throw new IllegalArgumentException("Deadline must be > 0, got " + deadline);
    }
    this.deadline = deadline;
    return this;
  }

  /**
   * Disables following of redirects.  Please read
   * the class javadoc for an explanation of how doNotFollowRedirects is used.
   * @return {@code this} (for chaining)
   */
  public FetchOptions doNotFollowRedirects() {
    this.followRedirects = false;
    return this;
  }

  boolean getAllowTruncate() {
    return allowTruncate;
  }

  boolean getFollowRedirects() {
    return followRedirects;
  }

  CertificateValidationBehavior getCertificateValidationBehavior() {
    return certificateValidationBehavior;
  }

  Double getDeadline() {
    return deadline;
  }

  /**
   * Contains static creation methods for {@link FetchOptions}.
   */
  public static final class Builder {

    /**
     * Create a {@link FetchOptions} that allows truncation of the response.
     * Shorthand for <code>FetchOptions.withDefaults().allowTruncate();</code>.
     * Please read the {@link FetchOptions} class javadoc for an explanation of
     * how response truncating works.
     * @return The newly created FetchOptions instance.
     */
    public static FetchOptions allowTruncate() {
      return withDefaults().allowTruncate();
    }

    /**
     * Create a {@link FetchOptions} that disallows truncation of the response.
     * Shorthand for
     * <code>FetchOptions.withDefaults().disallowTruncate();</code>.
     * Please read the {@link FetchOptions} class javadoc for an explanation of
     * how esponse truncating works.
     * @return The newly created FetchOptions instance.
     */
    public static FetchOptions disallowTruncate() {
      return withDefaults().disallowTruncate();
    }

    /**
     * Create a {@link FetchOptions} that follows redirects.
     * Shorthand for
     * <code>FetchOptions.withDefaults().followRedirects();</code>.
     * Please read the {@link FetchOptions} class javadoc for an explanation of
     * how redirection following works.
     * @return The newly created FetchOptions instance.
     */
    public static FetchOptions followRedirects() {
      return withDefaults().followRedirects();
    }

    /**
     * Create a {@link FetchOptions} that does not follow redirects.
     * Shorthand for
     * <code>FetchOptions.withDefaults().followRedirects();</code>.
     * Please read the {@link FetchOptions} class javadoc for an explanation of
     * how redirection following works.
     * @return The newly created FetchOptions instance.
     */
    public static FetchOptions doNotFollowRedirects() {
      return withDefaults().doNotFollowRedirects();
    }

    /**
     * Create a {@link FetchOptions} that performs SSL certificate validation.
     *
     * Shorthand for
     * <code>FetchOptions.withDefaults().validateCertificate();</code>.
     * Please read the {@link FetchOptions} class javadoc for an explanation of
     * how certificate validation works.
     * @return The newly created FetchOptions instance.
     */
    public static FetchOptions validateCertificate() {
      return withDefaults().validateCertificate();
    }

    /**
     * Create a {@link FetchOptions} that does not perform SSL certificate
     * validation.
     *
     * Shorthand for
     * <code>FetchOptions.withDefaults().doNotValidateCertificate();</code>.
     * Please read the {@link FetchOptions} class javadoc for an explanation of
     * how certificate validation works.
     * @return The newly created FetchOptions instance.
     */
    public static FetchOptions doNotValidateCertificate() {
      return withDefaults().doNotValidateCertificate();
    }

    /**
     * Create a {@link FetchOptions} with the specified deadline.
     * Shorthand for
     * <code>FetchOptions.withDefaults().setDeadline(deadline);</code>.
     * @return The newly created FetchOptions instance.
     */
    public static FetchOptions withDeadline(double deadline) {
      return withDefaults().setDeadline(deadline);
    }

    /**
     * Helper method for creating a {@link FetchOptions}
     * instance with default values.
     *
     * @see FetchOptions#DEFAULT_ALLOW_TRUNCATE
     * @see FetchOptions#DEFAULT_FOLLOW_REDIRECTS
     * @see FetchOptions#DEFAULT_DEADLINE
     */
    public static FetchOptions withDefaults() {
      return new FetchOptions();
    }

    private Builder() {}
  }
}
