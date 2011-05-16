// Copyright 2009 Google Inc. All Rights Reserved.
package com.google.appengine.tools.development.testing;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.ApiProxyLocalFactory;
import com.google.appengine.tools.development.Clock;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServerEnvironment;
import com.google.apphosting.api.ApiProxy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class for testing against local app engine services.
 * Construct the helper with one {@link LocalServiceTestConfig} instance for
 * each service that you wish to access as part of your test.  Then call
 * {@link #setUp()} before each test executes and {@link #tearDown()} after
 * each test executes.  No specific test-harness is assumed, but here's a
 * JUnit 3 example that uses task queues and the datastore.
 *
 * <blockquote>
 * <pre>
 * public void MyTest extends TestCase {
 *
 *   private final LocalServiceTestHelper helper = new LocalServiceTestHelper(
 *     new LocalTaskQueueTestConfig(), new LocalDatastoreServiceTestConfig());
 *
 *   &#64;Override
 *   public void setUp() {
 *     super.setUp();
 *     helper.setUp();
 *   }
 *
 *   &#64;Override
 *   public void tearDown() {
 *     helper.tearDown();
 *     super.tearDown();
 *   }
 * }
 * </pre>
 * </blockquote>
 *
*/
public class LocalServiceTestHelper {
  private static final String APPS_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".appsNamespace";

  static final String DEFAULT_APP_ID = "test";
  static final String DEFAULT_VERSION_ID = "1.0";

  private final Logger logger = Logger.getLogger(getClass().getName());
  private final List<LocalServiceTestConfig> configs;
  private String envAppId = DEFAULT_APP_ID;
  private String envVersionId = DEFAULT_VERSION_ID;
  private String envEmail;
  private boolean envIsLoggedIn;
  private boolean envIsAdmin;
  private String envAuthDomain;
  private Map<String, Object> envAttributes = new HashMap<String, Object>();
  private Clock clock;
  private boolean enforceApiDeadlines = false;

  /**
   * Constructs a LocalServiceTestHelper with the provided configurations.
   *
   * @param configs for the local services that need to be set up and torn down.
   */
  public LocalServiceTestHelper(LocalServiceTestConfig... configs) {
    this.configs = Arrays.asList(configs);
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getAppId()}
   *
   * @param envAppId
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvAppId(String envAppId) {
    this.envAppId = envAppId;
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getVersionId()}
   *
   * @param envVersionId
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvVersionId(String envVersionId) {
    this.envVersionId = envVersionId;
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getEmail()}
   *
   * @param envEmail
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvEmail(String envEmail) {
    this.envEmail = envEmail;
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().isLoggedIn()}
   *
   * @param envIsLoggedIn
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvIsLoggedIn(boolean envIsLoggedIn) {
    this.envIsLoggedIn = envIsLoggedIn;
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().isAdmin()}
   *
   * @param envIsAdmin
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvIsAdmin(boolean envIsAdmin) {
    this.envIsAdmin = envIsAdmin;
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getAuthDomain()}
   *
   * @param envAuthDomain
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvAuthDomain(String envAuthDomain) {
    this.envAuthDomain = envAuthDomain;
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getRequestNamespace()}
   *
   * @param envRequestNamespace
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvRequestNamespace(String envRequestNamespace) {
    envAttributes.put(APPS_NAMESPACE_KEY, envRequestNamespace);
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getAttributes()}
   *
   * @param envAttributes
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvAttributes(Map<String, Object> envAttributes) {
    this.envAttributes = envAttributes;
    return this;
  }

  /**
   * Sets the clock with which all local services will be initialized.  Note
   * that once a local service is initialized its clock cannot be altered.
   *
   * @param clock
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setClock(Clock clock) {
    this.clock = clock;
    return this;
  }

  /**
   * Determines whether or not API calls should be subject to the same
   * deadlines as in production.  The default is {@code false}.
   * @param val
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnforceApiDeadlines(boolean val) {
    this.enforceApiDeadlines = val;
    return this;
  }

  /**
   * Set up an environment in which tests that use local services can execute.
   *
   * @return {@code this} (for chaining)
   */
  public final LocalServiceTestHelper setUp() {
    ApiProxy.setEnvironmentForCurrentThread(newEnvironment());
    ApiProxyLocal proxyLocal = new ApiProxyLocalFactory().create(newLocalServerEnvironment());
    if (clock != null) {
      proxyLocal.setClock(clock);
    }
    ApiProxy.setDelegate(proxyLocal);

    for (LocalServiceTestConfig config : configs) {
      config.setUp();
    }
    return this;
  }

  /**
   * Constructs the {@link ApiProxy.Environment} that will be installed.
   * Subclass and override to provide your own implementation.
   */
  protected ApiProxy.Environment newEnvironment() {
    return new TestEnvironment() {

      @Override
      public String getAppId() {
        return envAppId;
      }

      @Override
      public String getVersionId() {
        return envVersionId;
      }

      @Override
      public String getEmail() {
        return envEmail;
      }

      @Override
      public boolean isLoggedIn() {
        return envIsLoggedIn;
      }

      @Override
      public boolean isAdmin() {
        return envIsAdmin;
      }

      @Override
      public String getAuthDomain() {
        return envAuthDomain;
      }

      @Override
      public Map<String, Object> getAttributes() {
        return envAttributes;
      }
    };
  }

  /**
   * Constructs a new {@link ApiProxy.Environment} by copying the data from the
   * given one. The {@code Map} from {@code getAttributes} will be
   * shallow-copied.
   */
  static ApiProxy.Environment copyEnvironment(ApiProxy.Environment copyFrom){
    return new TestEnvironment(copyFrom);
  }

  /**
   * Constructs a new default {@link ApiProxy.Environment}.
   */
  static ApiProxy.Environment newDefaultTestEnvironment() {
    return new TestEnvironment();
  }

  private static class TestEnvironment implements  ApiProxy.Environment {
    private String appId = LocalServiceTestHelper.DEFAULT_APP_ID;
    private String versionId = LocalServiceTestHelper.DEFAULT_VERSION_ID;
    private String email;
    private boolean isLoggedIn;
    private boolean isAdmin;
    private String authDomain;
    private Map<String, Object> attributes = new HashMap<String, Object>();

    private TestEnvironment() {}

    private TestEnvironment(String appId,
        String versionId,
        String email,
        boolean isLoggedIn,
        boolean isAdmin,
        String authDomain,
        Map<String, Object> attributes) {
      this.appId = appId;
      this.versionId = versionId;
      this.email = email;
      this.isLoggedIn = isLoggedIn;
      this.isAdmin = isAdmin;
      this.authDomain = authDomain;
      this.attributes = attributes;
    }

    public TestEnvironment(ApiProxy.Environment copyFrom) {
      this(copyFrom.getAppId(),
          copyFrom.getVersionId(),
          copyFrom.getEmail(),
          copyFrom.isLoggedIn(),
          copyFrom.isAdmin(),
          copyFrom.getAuthDomain(),
          new HashMap<String, Object>(copyFrom.getAttributes()));
    }

    @Override
    public String getAppId() {
      return appId;
    }

    @Override
    public String getVersionId() {
      return versionId;
    }

    @Override
    public String getEmail() {
      return email;
    }

    @Override
    public boolean isLoggedIn() {
      return isLoggedIn;
    }

    @Override
    public boolean isAdmin() {
      return isAdmin;
    }

    @Override
    public String getAuthDomain() {
      return authDomain;
    }

    @Override
    @Deprecated
    public String getRequestNamespace() {
      throw new IllegalArgumentException("getRequestNamespace() is no longer supported. "
          + "Use NamespaceManager.getGoogleAppsDomain() instead.");
    }

    @Override
    public Map<String, Object> getAttributes() {
      return attributes;
    }

  }

  /**
   * Constructs the {@link LocalServerEnvironment} that will be installed.
   * Subclass and override to provide your own implementation.
   */
  protected LocalServerEnvironment newLocalServerEnvironment() {
    return new TestLocalServerEnvironment(enforceApiDeadlines);
  }

  /**
   * Tear down the environment in which tests that use local services can
   * execute.
   */
  public final void tearDown() {
    RuntimeException firstException = null;
    for (LocalServiceTestConfig config : configs) {
      try {
        config.tearDown();
      } catch (RuntimeException rte) {
        if (firstException == null) {
          firstException = rte;
        } else {
          logger.log(
              Level.SEVERE,
              "Received exception tearing down config of type " + config.getClass().getName(),
              rte);
        }
      }
    }
    if (firstException != null) {
      throw firstException;
    }
    ApiProxy.setDelegate(null);
    ApiProxy.setEnvironmentForCurrentThread(null);
  }

  /**
   * Convenience function for getting ahold of the currently
   * registered {@link ApiProxyLocal}.
   */
  public static ApiProxyLocal getApiProxyLocal() {
    return (ApiProxyLocal) ApiProxy.getDelegate();
  }

  /**
   * Convenience function for getting ahold of a specific local service.
   * For example, to get ahold of the LocalDatastoreService you would
   * call {@code getLocalService(LocalDatastoreService.PACKAGE)}.
   */
  public static LocalRpcService getLocalService(String serviceName) {
    return getApiProxyLocal().getService(serviceName);
  }
}
