// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.util;

import com.google.common.base.Joiner;

import java.util.List;

/**
 * A command line action.
*
*/
public abstract class Action {

  private final String[] names;
  private List<String> args;

  public Action(String... names) {
    this.names = names;
  }

  public String[] getNames() {
    return names;
  }

  public String getNameString() {
    return Joiner.on(" ").join(names);
  }

  protected void setArgs(List<String> args) {
    this.args = args;
  }

  public List<String> getArgs() {
    return args;
  }

  /**
   * Reads the value parsed for the {@code Action} and applies the
   * appropriate logic.
   *
   * @throws IllegalArgumentException if the parsed value is invalid.
   */
  public abstract void apply();
}
