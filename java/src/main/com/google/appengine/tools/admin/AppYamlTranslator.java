// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.admin;

import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml.AdminConsolePage;
import com.google.apphosting.utils.config.AppEngineWebXml.ErrorHandler;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.BackendsXml;
import com.google.apphosting.utils.config.WebXml.SecurityConstraint;
import com.google.apphosting.utils.config.WebXml;
import com.google.apphosting.utils.glob.ConflictResolver;
import com.google.apphosting.utils.glob.Glob;
import com.google.apphosting.utils.glob.GlobFactory;
import com.google.apphosting.utils.glob.GlobIntersector;
import com.google.apphosting.utils.glob.LongestPatternConflictResolver;
import com.google.common.base.Join;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.Collections;

/**
 * Generates {@code app.yaml} files suitable for uploading as part of
 * a Google App Engine application.
 *
 */
public class AppYamlTranslator {
  private static final String NO_API_VERSION = "none";

  private static final ConflictResolver RESOLVER =
      new LongestPatternConflictResolver();

  private static final String DYNAMIC_PROPERTY = "dynamic";
  private static final String STATIC_PROPERTY = "static";
  private static final String WELCOME_FILES = "welcome";
  private static final String TRANSPORT_GUARANTEE_PROPERTY = "transportGuarantee";
  private static final String REQUIRED_ROLE_PROPERTY = "requiredRole";
  private static final String EXPIRATION_PROPERTY = "expiration";

  private static final String[] PROPERTIES = new String[] {
    DYNAMIC_PROPERTY,
    STATIC_PROPERTY,
    WELCOME_FILES,
    TRANSPORT_GUARANTEE_PROPERTY,
    REQUIRED_ROLE_PROPERTY,
    EXPIRATION_PROPERTY,
  };

  private static final int MAX_HANDLERS = 100;

  private final AppEngineWebXml appEngineWebXml;
  private final WebXml webXml;
  private final BackendsXml backendsXml;
  private final String apiVersion;
  private final Set<String> staticFiles;

  public AppYamlTranslator(AppEngineWebXml appEngineWebXml,
                           WebXml webXml,
                           BackendsXml backendsXml,
                           String apiVersion,
                           Set<String> staticFiles) {
    this.appEngineWebXml = appEngineWebXml;
    this.webXml = webXml;
    this.backendsXml = backendsXml;
    this.apiVersion = apiVersion;
    this.staticFiles = staticFiles;
  }

  public String getYaml() {
    StringBuilder builder = new StringBuilder();
    translateAppEngineWebXml(builder);
    translateApiVersion(builder);
    translateWebXml(builder);
    return builder.toString();
  }

  private void translateAppEngineWebXml(StringBuilder builder) {
    builder.append("application: '" + appEngineWebXml.getAppId() + "'\n");
    builder.append("runtime: java\n");
    if (appEngineWebXml.getMajorVersionId() != null) {
      builder.append("version: '" + appEngineWebXml.getMajorVersionId() + "'\n");
    }

    Collection<String> services = appEngineWebXml.getInboundServices();
    if (!services.isEmpty()) {
      builder.append("inbound_services:\n");
      for (String service : services) {
        builder.append("- " + service + "\n");
      }
    }

    if (appEngineWebXml.getPrecompilationEnabled()) {
      builder.append("derived_file_type:\n");
      builder.append("- java_precompiled\n");
    }

    if (appEngineWebXml.getThreadsafe()) {
      builder.append("threadsafe: True\n");
    }

    List<AdminConsolePage> adminConsolePages = appEngineWebXml.getAdminConsolePages();
    if (!adminConsolePages.isEmpty()) {
      builder.append("admin_console:\n");
      builder.append("  pages:\n");
      for (AdminConsolePage page : adminConsolePages) {
        builder.append("  - name: " + page.getName() + "\n");
        builder.append("    url: " + page.getUrl() + "\n");
      }
    }

    List<ErrorHandler> errorHandlers = appEngineWebXml.getErrorHandlers();
    if (!errorHandlers.isEmpty()) {
      builder.append("error_handlers:\n");
      for (ErrorHandler handler : errorHandlers) {
        String fileName = handler.getFile();
        if (!fileName.startsWith("/")) {
          fileName = "/" + fileName;
        }
        if (!staticFiles.contains("__static__" + fileName)) {
          throw new AppEngineConfigException("No static file found for error handler: " +
                                             fileName + ", out of " + staticFiles);
        }
        builder.append("- file: __static__" + fileName + "\n");
        if (handler.getErrorCode() != null) {
          builder.append("  error_code: " + handler.getErrorCode() + "\n");
        }
        String mimeType = webXml.getMimeTypeForPath(handler.getFile());
        if (mimeType != null) {
          builder.append("  mime_type: " + mimeType + "\n");
        }
      }
    }

    if (backendsXml != null) {
      List<BackendsXml.Entry> backends = backendsXml.getBackends();
      if (!backends.isEmpty()) {
        builder.append("backends:\n");
        for (BackendsXml.Entry entry : backends) {
          builder.append("- name: '" + entry.getName() + "'\n");
          if (entry.getInstances() != null) {
            builder.append("  instances: " + entry.getInstances() + "\n");
          }
          if (entry.getInstanceClass() != null) {
            builder.append("  class: '" + entry.getInstanceClass() + "'\n");
          }
          if (entry.getMaxConcurrentRequests() != null) {
            builder.append("  max_concurrent_requests: " + entry.getMaxConcurrentRequests() + "\n");
          }
          List<String> options = new ArrayList<String>();
          for (BackendsXml.Option option : entry.getOptions()) {
            options.add(option.getYamlValue());
          }
          if (!options.isEmpty()) {
            builder.append("  options: " + Join.join(", ", options) + "\n");
          }
        }
      }
    }
  }

  private String formatBoolean(boolean flag) {
    return flag ? "True" : "False";
  }

  private void translateApiVersion(StringBuilder builder) {
    if (apiVersion == null) {
      builder.append("api_version: '" + NO_API_VERSION + "'\n");
    } else {
      builder.append("api_version: '" + apiVersion + "'\n");
    }
  }

  private void translateWebXml(StringBuilder builder) {
    builder.append("handlers:\n");

    AbstractHandlerGenerator staticGenerator = null;
    if (staticFiles.isEmpty()) {
      staticGenerator = new EmptyHandlerGenerator();
    } else {
      staticGenerator = new StaticHandlerGenerator(appEngineWebXml.getPublicRoot());
    }

    DynamicHandlerGenerator dynamicGenerator =
        new DynamicHandlerGenerator(webXml.getFallThroughToRuntime());
    if (staticGenerator.size() + dynamicGenerator.size() > MAX_HANDLERS) {
      dynamicGenerator = new DynamicHandlerGenerator(true);
    }

    staticGenerator.translate(builder);
    dynamicGenerator.translate(builder);
  }

  class StaticHandlerGenerator extends AbstractHandlerGenerator {
    private final String root;
    private final String rootRegex;

    public StaticHandlerGenerator(String root) {
      this.root = root;
      this.rootRegex = root.replaceAll("([^A-Za-z0-9\\-_/])", "\\\\$1");
    }

    @Override
    protected Map<String, Object> getWelcomeProperties() {
      List<String> staticWelcomeFiles = new ArrayList<String>();
      for (String welcomeFile : webXml.getWelcomeFiles()) {
        for (String staticFile : staticFiles) {
          if (staticFile.endsWith("/" + welcomeFile)) {
            staticWelcomeFiles.add(welcomeFile);
            break;
          }
        }
      }
      return Collections.<String,Object>singletonMap(WELCOME_FILES, staticWelcomeFiles);
    }

    @Override
    protected void addPatterns(GlobIntersector intersector) {
      List<AppEngineWebXml.StaticFileInclude> includes = appEngineWebXml.getStaticFileIncludes();
      if (includes.isEmpty()) {
        intersector.addGlob(GlobFactory.createGlob("/*", STATIC_PROPERTY, true));
      } else {
        for (AppEngineWebXml.StaticFileInclude include : includes) {
          String pattern = include.getPattern().replaceAll("\\*\\*", "*");
          if (!pattern.startsWith("/")) {
            pattern = "/" + pattern;
          }
          Map<String, Object> props = new HashMap<String, Object>();
          props.put(STATIC_PROPERTY, true);
          if (include.getExpiration() != null) {
            props.put(EXPIRATION_PROPERTY, include.getExpiration());
          }
          intersector.addGlob(GlobFactory.createGlob(pattern, props));
        }
      }
    }

    @Override
    public void translateGlob(StringBuilder builder, Glob glob) {
      String regex = glob.getRegularExpression().pattern();

      @SuppressWarnings("unchecked")
      List<String> welcomeFiles =
          (List<String>) glob.getProperty(WELCOME_FILES, RESOLVER);
      if (welcomeFiles != null) {
        for (String welcomeFile : welcomeFiles) {
          builder.append("- url: (" + regex + ")\n");
          builder.append("  static_files: __static__" + root + "\\1" + welcomeFile + "\n");
          builder.append("  upload: __static__" + rootRegex + regex + welcomeFile + "\n");
          builder.append("  require_matching_file: True\n");
          translateHandlerOptions(builder, glob);
          translateAdditionalStaticOptions(builder, glob);
        }
      } else {
        Boolean isStatic = (Boolean) glob.getProperty(STATIC_PROPERTY, RESOLVER);
        if (isStatic != null && isStatic.booleanValue()) {
          builder.append("- url: (" + regex + ")\n");
          builder.append("  static_files: __static__" + root + "\\1\n");
          builder.append("  upload: __static__" + rootRegex + regex + "\n");
          builder.append("  require_matching_file: True\n");
          translateHandlerOptions(builder, glob);
          translateAdditionalStaticOptions(builder, glob);
        }
      }
    }

    private void translateAdditionalStaticOptions(StringBuilder builder, Glob glob) {
      String expiration = (String) glob.getProperty(EXPIRATION_PROPERTY, RESOLVER);
      if (expiration != null) {
        builder.append("  expiration: " + expiration + "\n");
      }
    }
  }

  class DynamicHandlerGenerator extends AbstractHandlerGenerator {
    private final List<String> patterns;
    private boolean fallthrough;
    private boolean hasJsps;

    DynamicHandlerGenerator(boolean alwaysFallthrough) {
      fallthrough = alwaysFallthrough;
      patterns = new ArrayList<String>();
      for (String servletPattern : webXml.getServletPatterns()) {
        if (servletPattern.equals("/") || servletPattern.equals("/*")) {
          fallthrough = true;
        } else if (servletPattern.endsWith(".jsp")) {
          hasJsps = true;
        } else {
          patterns.add(servletPattern);
        }
      }
    }

    @Override
    protected Map<String, Object> getWelcomeProperties() {
      if (fallthrough) {
        return null;
      } else {
        return Collections.<String,Object>singletonMap(DYNAMIC_PROPERTY, true);
      }
    }

    @Override
    protected void addPatterns(GlobIntersector intersector) {
      if (fallthrough) {
        intersector.addGlob(GlobFactory.createGlob(
            "/*",
            DYNAMIC_PROPERTY, true));
      } else {
        for (String servletPattern : patterns) {
          intersector.addGlob(GlobFactory.createGlob(
              servletPattern,
              DYNAMIC_PROPERTY, true));
          if (servletPattern.endsWith("/*")) {
            intersector.addGlob(GlobFactory.createGlob(
                servletPattern.substring(0, servletPattern.length() - 2),
                DYNAMIC_PROPERTY, true));
          }
        }
        if (hasJsps) {
          intersector.addGlob(GlobFactory.createGlob(
              "*.jsp",
              DYNAMIC_PROPERTY, true));
        }
        intersector.addGlob(GlobFactory.createGlob(
            "/_ah/*",
            DYNAMIC_PROPERTY, true));
      }
    }

    @Override
    public void translateGlob(StringBuilder builder, Glob glob) {
      String regex = glob.getRegularExpression().pattern();

      Boolean isDynamic = (Boolean) glob.getProperty(DYNAMIC_PROPERTY, RESOLVER);
      if (isDynamic != null && isDynamic.booleanValue()) {
        builder.append("- url: " + regex + "\n");
        builder.append("  script: unused\n");
        translateHandlerOptions(builder, glob);
      }
    }
  }

  /**
   * An {@code AbstractHandlerGenerator} that returns no globs.
   */
  class EmptyHandlerGenerator extends AbstractHandlerGenerator {
    @Override
    protected void addPatterns(GlobIntersector intersector) {
    }

    @Override
    protected void translateGlob(StringBuilder builder, Glob glob) {
    }

    @Override
    protected Map<String, Object> getWelcomeProperties() {
      return Collections.emptyMap();
    }
  }

  abstract class AbstractHandlerGenerator {
    private List<Glob> globs = null;

    public int size() {
      return getGlobPatterns().size();
    }

    public void translate(StringBuilder builder) {
      for (Glob glob : getGlobPatterns()) {
        translateGlob(builder, glob);
      }
    }

    abstract protected void addPatterns(GlobIntersector intersector);
    abstract protected void translateGlob(StringBuilder builder, Glob glob);

    /**
     * @returns a map of welcome properties to apply to the welcome
     * file entries, or {@code null} if no welcome file entries are
     * necessary.
     */
    abstract protected Map<String, Object> getWelcomeProperties();

    protected List<Glob> getGlobPatterns() {
      if (globs == null) {
        GlobIntersector intersector = new GlobIntersector();
        addPatterns(intersector);
        addSecurityConstraints(intersector);
        addWelcomeFiles(intersector);

         globs = intersector.getIntersection();
         removeNearDuplicates(globs);
      }
      return globs;
    }

    protected void addWelcomeFiles(GlobIntersector intersector) {
      Map<String, Object> welcomeProperties = getWelcomeProperties();
      if (welcomeProperties != null) {
        intersector.addGlob(GlobFactory.createGlob("/", welcomeProperties));
        intersector.addGlob(GlobFactory.createGlob("/*/", welcomeProperties));
      }
    }

    protected void addSecurityConstraints(GlobIntersector intersector) {
      for (SecurityConstraint constraint : webXml.getSecurityConstraints()) {
        for (String pattern : constraint.getUrlPatterns()) {
          intersector.addGlob(GlobFactory.createGlob(
              pattern,
              TRANSPORT_GUARANTEE_PROPERTY,
              constraint.getTransportGuarantee()));
          intersector.addGlob(GlobFactory.createGlob(
              pattern,
              REQUIRED_ROLE_PROPERTY,
              constraint.getRequiredRole()));
        }
      }
    }

    protected void translateHandlerOptions(StringBuilder builder, Glob glob) {
      SecurityConstraint.RequiredRole requiredRole =
          (SecurityConstraint.RequiredRole) glob.getProperty(REQUIRED_ROLE_PROPERTY, RESOLVER);
      if (requiredRole == null) {
        requiredRole = SecurityConstraint.RequiredRole.NONE;
      }
      switch (requiredRole) {
        case NONE:
          builder.append("  login: optional\n");
          break;
        case ANY_USER:
          builder.append("  login: required\n");
          break;
        case ADMIN:
          builder.append("  login: admin\n");
          break;
      }

      SecurityConstraint.TransportGuarantee transportGuarantee =
          (SecurityConstraint.TransportGuarantee) glob.getProperty(TRANSPORT_GUARANTEE_PROPERTY,
                                                                   RESOLVER);
      if (transportGuarantee == null) {
        transportGuarantee = SecurityConstraint.TransportGuarantee.NONE;
      }
      switch (transportGuarantee) {
        case NONE:
          if (appEngineWebXml.getSslEnabled()) {
            builder.append("  secure: optional\n");
          } else {
            builder.append("  secure: never\n");
          }
          break;
        case INTEGRAL:
        case CONFIDENTIAL:
          if (!appEngineWebXml.getSslEnabled()) {
            throw new AppEngineConfigException(
                "SSL must be enabled in appengine-web.xml to use transport-guarantee");
          }
          builder.append("  secure: always\n");
          break;
      }
    }

    private void removeNearDuplicates(List<Glob> globs) {
      for (int i = 0; i < globs.size(); i++) {
        Glob topGlob = globs.get(i);
        for (int j = i + 1; j < globs.size(); j++) {
          Glob bottomGlob = globs.get(j);
          if (bottomGlob.matchesAll(topGlob)) {
            if (propertiesMatch(topGlob, bottomGlob)) {
              globs.remove(i);
              i--;
            }
            break;
          }
        }
      }
    }

    private boolean propertiesMatch(Glob glob1, Glob glob2) {
      for (String property : PROPERTIES) {
        Object value1 = glob1.getProperty(property, RESOLVER);
        Object value2 = glob2.getProperty(property, RESOLVER);
        if (value1 != value2 && (value1 == null || !value1.equals(value2))) {
          return false;
        }
      }
      return true;
    }
  }
}
