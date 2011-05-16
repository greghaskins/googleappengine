// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import java.security.Permissions;

/**
 * A container-agnostic interface to the currently loaded webapp.
 *
 */
public interface AppContext {

  /**
   * Returns the ClassLoader for the webapp.
   *
   * @return a non-null ClassLoader
   */
  public ClassLoader getClassLoader();

  /**
   * Returns a {@link java.security.Permissions} object containing {@link
   * java.security.UnresolvedPermission} instances for every
   * permission that was requested by the user in their {@code
   * appengine-web.xml} file.
   *
   * <p>Note that user code will not actually run with these
   * permissions.  However, to user-provided code that calls {@code
   * SecurityManager.checkPermission} directly it will appear that it
   * is.  This is designed primarily for third-party libraries that
   * introduce their own {@link java.security.Permission} subclasses
   * that are not used by any other classes.
   *
   * @return a non-null set of Permissions
   */
  public Permissions getUserPermissions();

  /**
   * Returns the set of Permissions granted to the application,
   * excluding {@link #getUserPermissions user-specified permissions}.
   *
   * @return a non-null set of Permissions
   */
  public Permissions getApplicationPermissions();

  /**
   * Returns the container-specific context implementation.
   *
   * @return a non-null context, represented by a container-specific class.
   * For example, Jetty's {@code org.mortbay.jetty.webapp.WebAppContext}.
   */
  public Object getContainerContext();
}
