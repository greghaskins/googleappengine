// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development.testing;

import com.google.appengine.api.rdbms.dev.LocalRdbmsProperties;
import com.google.appengine.api.rdbms.dev.LocalRdbmsService;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.cloud.sql.jdbc.internal.SqlClientFactory;

import org.hsqldb.jdbcDriver;

import java.util.Iterator;
import java.util.Map;

/**
 * Config for accessing the local RDBMS service in tests.
 * Default behavior is to configure the local service to use an in-memory
 * database.  {@link #tearDown()} does not wipe out data or tables.
 *
 */
public class LocalRdbmsServiceTestConfig implements LocalServiceTestConfig {

  private String driverClass = jdbcDriver.class.getName();
  private String driverUrl = "jdbc:hsqldb:mem:%s";
  private LocalRdbmsService.ServerType serverType = LocalRdbmsService.ServerType.LOCAL;
  private String extraDriverProperties = "";
  private Class<? extends SqlClientFactory> remoteClientFactory = null;

  @Override
  public void setUp() {
    ApiProxyLocal proxy = LocalServiceTestHelper.getApiProxyLocal();
    proxy.setProperty(LocalRdbmsProperties.DRIVER_PROPERTY, driverClass);
    proxy.setProperty(LocalRdbmsProperties.JDBC_CONNECTION_URL_STRING, driverUrl);
    proxy.setProperty(LocalRdbmsService.SERVER_TYPE, serverType.flagValue());
    proxy.setProperty(LocalRdbmsProperties.EXTRA_DRIVER_PROPERTIES, extraDriverProperties);
    if (remoteClientFactory != null) {
      proxy.setProperty(LocalRdbmsProperties.REMOTE_CLIENT_FACTORY, remoteClientFactory.getName());
    }
  }

  @Override
  public void tearDown() {
  }

  public static LocalRdbmsService getLocalRdbmsService() {
    return (LocalRdbmsService) LocalServiceTestHelper.getLocalService(LocalRdbmsService.PACKAGE);
  }

  /**
   * @return The driver class.
   */
  public String getDriverClass() {
    return driverClass;
  }

  /**
   * Sets the class of the driver used by the
   * {@link com.google.appengine.api.rdbms.dev.LocalRdbmsServiceLocalDriver} and
   * attempts to load {@code driverClass} in the current {@link ClassLoader}.
   *
   * @param driverClass The driver class.  Must be the fully-qualified name
   * of a class that implements {@link java.sql.Driver}.
   * @return {@code this} (for chaining)
   * @throws RuntimeException wrapping any exceptions loading driverClass.
   */
  public LocalRdbmsServiceTestConfig setDriverClass(String driverClass) {
    this.driverClass = driverClass;
    try {
      Class.forName(driverClass);
    } catch (Throwable t) {
      if (t instanceof RuntimeException) {
        throw (RuntimeException) t;
      }
      throw new RuntimeException(t);
    }
    return this;
  }

  /**
   * @return The JDBC connection string format
   */
  public String getJdbcConnectionStringFormat() {
    return driverUrl;
  }

  /**
   * Sets the format of the connection string that the jdbc driver will use.
   *
   * @param jdbcConnectionStringFormat the connection string format
   * @return {@code this} (for chaining)
   */
  public LocalRdbmsServiceTestConfig setJdbcConnectionStringFormat(
          String jdbcConnectionStringFormat) {
    this.driverUrl = jdbcConnectionStringFormat;
    return this;
  }

  /**
   * Sets the server type to either {@code hosted} or {@code local}.
   *
   * <p><ul>
   * <li>{@code local} connections proxy the SQL Service wire format to a local
   * database using JDBC.
   * <li>{@code remote} connections talk over a {@code SpeckleRpc} to a hosted
   * development mode Speckle instance.
   * </ul>
   *
   * @param serverType hosted or local
   * @return {@code this} (for chaining)
   * @throws IllegalArgumentException if serverType is not "hosted" or "local"
   */
  public LocalRdbmsServiceTestConfig setServerType(LocalRdbmsService.ServerType serverType) {
    if (serverType == null) {
      throw new NullPointerException("serverType can not be null");
    }
    this.serverType = serverType;
    return this;
  }

  /**
   * Sets Extra properties to be passed to the underlying {@code local} JDBC
   * driver or the RPC class for the {@code remote} connection.
   *
   * @param props the extra driver properties.
   * @return {@code this} (for chaining)
   */
  public LocalRdbmsServiceTestConfig setExtraDriverProperties(Map<String, String> props) {
    this.extraDriverProperties = mapToString(props);
    return this;
  }

  /**
   * Sets the remote client factory class.
   *
   * @param remoteClientFactory the SpeckleClientFactory implementation.
   * @return {@code this} (for chaining)
   */
  public LocalRdbmsServiceTestConfig setRemoteClientFactory(
      Class<? extends SqlClientFactory> remoteClientFactory) {
    this.remoteClientFactory = remoteClientFactory;
    return this;
  }

  /**
   * Converts a {@link Map} into a String of {@code k=v(,k=v)*}
   */
  private static String mapToString(Map<String, String> map) {
    StringBuilder out = new StringBuilder();
    Iterator<Map.Entry<String, String>> iterator = map.entrySet().iterator();
    if (iterator.hasNext()) {
      Map.Entry<String, String> entry = iterator.next();
      out.append(entry.getKey()).append("=").append(entry.getValue());
      while (iterator.hasNext()) {
        entry = iterator.next();
        out.append(",").append(entry.getKey()).append("=").append(entry.getValue());
      }
    }
    return out.toString();
  }
}
