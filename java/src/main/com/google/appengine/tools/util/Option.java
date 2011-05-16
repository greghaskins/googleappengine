// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.util;

import java.util.StringTokenizer;

/**
 * A command line option. There may be multiple command line options for a single
 * command line. Each option may be represented by a "short" name or a "long" name.
 * A short option is prefixed by a single dash ("-"), and its value follows in a separate
 * argument. For example, <pre>
 * -e foo@example.com
 * </pre>
 * A long option is prefixed by a double dash ("--"), and its value is specified with an
 * equals sign ("="). For example, <pre>
 * --email=foo@example.com
 * </pre>
 * Flag-style {@code Options} have no specified value, and imply their meaning by mere
 * presence. For example, <pre>
 * --append (Forces appending to an existing file)</pre>
 *
 */
public abstract class Option {

  public enum Style {
    Short,
    Long
  }

  private final String shortName;

  private final String longName;

  private final boolean isFlag;

  private Style style;

  private String value;

  /**
   * Creates a new {@code Option}. While both {@code shortName} and
   * {@code longName} are optional, one must be supplied.
   *
   * @param shortName The short name to support. May be {@code null}.
   * @param longName The long name to support. May be {@code null}.
   * @param isFlag true to indicate that the Option represents a boolean value.
   */
  public Option(String shortName, String longName, boolean isFlag) {
    this.shortName = shortName;
    this.longName = longName;
    this.isFlag = isFlag;
  }

  /**
   * Returns true if the {@code Option} represents a boolean value.
   * Flags are always specified using one single argument, for example,
   * "-h" or "--help". They are never of the form, "-h true" or "--help=true".
   */
  public boolean isFlag() {
    return isFlag;
  }

  /**
   * Returns the {@code Style} in which the {@code Option} was supplied
   * on the command line.
   */
  public Style getArgStyle() {
    return style;
  }

  /**
   * Returns the value that was supplied for the {@code Option} on
   * the command line.
   */
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return "Option{" + "shortName='" + shortName + '\'' + ", longName='" + longName + '\''
        + ", isFlag=" + isFlag + ", style=" + style + ", value='" + value + '\'' + '}';
  }

  /**
   * Reads the value parsed for the {@code Option} and applies the
   * appropriate logic.
   *
   * @throws IllegalArgumentException if the parsed value is invalid.
   */
  public abstract void apply();

  boolean parse(String[] args, int currentArg) {
    String argVal = args[currentArg];

    if (shortName != null) {
      if (argVal.equals("-" + shortName)) {
        if (isFlag()) {
          return true;
        }
        if (currentArg + 1 == args.length) {
          throw new IllegalArgumentException(shortName + " requires an argument.\n");
        }
        value = args[currentArg + 1];
        style = Style.Short;
        return true;
      }
    }

    if (longName != null) {
      if (isFlag()) {
        return argVal.equals("--" + longName);
      }
      if (!argVal.startsWith("--" + longName + "=")) {
        return false;
      }
      StringTokenizer st = new StringTokenizer(argVal, "=");
      st.nextToken();
      if (!st.hasMoreTokens()) {
        throw new IllegalArgumentException(
            longName + " requires an argument, for example, \"" + longName + "=FOO\"\n");
      }
      value = st.nextToken();
      style = Style.Long;
      return true;
    }

    return false;
  }
}
