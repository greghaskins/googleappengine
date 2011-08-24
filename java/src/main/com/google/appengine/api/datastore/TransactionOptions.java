// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.appengine.api.datastore;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes options for transactions, passed at transaction creation time.
 *
 * <p>{@code multipleEntityGroups} is a boolean that enables or disables the
 * use of multiple entity groups in a single transaction.
 *
 * <p>Notes on usage:<br>
 * The recommended way to instantiate a {@code TransactionsOptions} object is to
 * statically import {@link Builder}.* and invoke a static
 * creation method followed by an instance mutator (if needed):
 *
 * <blockquote>
 * <pre>
 * import static com.google.appengine.api.datastore.TransactionOptions.Builder.*;
 *
 * ...
 *
 * datastoreService.beginTransaction(allowMultipleEntityGroups(true));
 *
 * </pre>
 * </blockquote>
 *
 */
public final class TransactionOptions {

  private Boolean multipleEntityGroups;

  private TransactionOptions() {
  }

  TransactionOptions(TransactionOptions original) {
    this.multipleEntityGroups = original.multipleEntityGroups;
  }

  /**
   * Enable or disable the use of multiple entity groups in a single transaction.
   *
   * @param enable true to allow multiple entity groups in a transaction, false to
   *   restrict transactions to a single entity group.
   * @return {@code this} (for chaining)
   */
  public TransactionOptions multipleEntityGroups(boolean enable) {
    this.multipleEntityGroups = enable;
    return this;
  }

  public TransactionOptions clearMultipleEntityGroups() {
    this.multipleEntityGroups = null;
    return this;
  }

  /**
   * @return {@code true} if multiple entity groups are allowed in the
   *   transaction, {@code false} if they are not allowed, or {@code null} if
   *   the setting was not provided.
   */
  public Boolean allowsMultipleEntityGroups() {
    return multipleEntityGroups;
  }

  @Override
  public int hashCode() {
    int result = 0;

    if (multipleEntityGroups != null) {
      result = result * 31 + (multipleEntityGroups ? 1 : 0);
    }

    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }

    if (obj.getClass() != this.getClass()) {
      return false;
    }

    TransactionOptions that = (TransactionOptions) obj;

    if (multipleEntityGroups != null) {
      if (!multipleEntityGroups.equals(that.multipleEntityGroups)) {
        return false;
      }
    } else if (that.multipleEntityGroups != null) {
      return false;
    }

    return true;
  }

  @Override
  public String toString() {
    List<String> result = new ArrayList<String>();

    if (multipleEntityGroups != null) {
      result.add("multipleEntityGroups=" + multipleEntityGroups);
    }

    return "TransactionOptions" + result;
  }

  /**
   * Contains static creation methods for {@link TransactionOptions}.
   */
  public static final class Builder {
    /**
     * Create a {@link TransactionOptions} that enables or disables the use of
     * multiple entity groups in a single transaction. Shorthand for
     * <code>TransactionOptions.withDefaults().allowMultipleEntityGroups(...);</code>
     *
     * @param enable true to allow multiple entity groups in a transaction, false to
     *   restrict transactions to a single entity group.
     * @return {@code this} (for chaining)
     */
    public static TransactionOptions allowMultipleEntityGroups(boolean enable) {
      return withDefaults().multipleEntityGroups(enable);
    }

    /**
     * Helper method for creating a {@link TransactionOptions} instance with
     * default values.  The defaults are {@code null} for all values.
     */
    public static TransactionOptions withDefaults() {
      return new TransactionOptions();
    }

    private Builder() {}
  }
}
