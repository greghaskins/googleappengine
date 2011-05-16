package com.google.appengine.api.capabilities;

/**
 * A capability represents a particular feature or set of features available on
 * the App Engine platform.
 *
 * To check the availability of a particular capability, use the
 * CapabilitiesService} API.
 *
 *
 */
public class Capability {
  /**
   * Availability of BlobstoreService.
   */
  public final static Capability BLOBSTORE = new Capability("blobstore");
  /**
   * Availability of the datastore.
   */
  public final static Capability DATASTORE = new Capability("datastore_v3");
  /**
   * Availability of datastore writes.
   */
  public final static Capability DATASTORE_WRITE = new Capability("datastore_v3", "write");
  /**
   * Availability of the ImagesService.
   */
  public final static Capability IMAGES = new Capability("images");
  /**
   * Availability of theMailService.
   */
  public final static Capability MAIL = new Capability("mail");
  /**
   * Availability ofMemcacheService.
   */
  public final static Capability MEMCACHE = new Capability("memcache");
  /**
   * Availability of TaskQueueService.
   */
  public final static Capability TASKQUEUE = new Capability("taskqueue");
  /**
   * Availability of the URLFetchService.
   */
  public final static Capability URL_FETCH = new Capability("urlfetch");
  /**
   * Availability of the XMPPService.
   */
  public final static Capability XMPP = new Capability("xmpp");

  private final String packageName;
  private final String name;

  /**
   *
   * Creates a new instance of a Capability.
   *
   * @param packageName name of the package associated with this capability.
   *
   */
  public Capability(String packageName) {
    this(packageName, "*");
  }

  /**
   * Creates a new instance of a Capability.
   *
   * @param packageName name of the package associated with this capability.
   * @param name name of the capability.
   */
  public Capability(String packageName, String name) {
    this.packageName = packageName;
    this.name = name;
  }

  /**
   * Returns the package name associated with this capability.
   *
   * @return the package name associated with this capability.
   */
  public String getPackageName() {
    return packageName;
  }

  /**
   * Returns the name associated with this capability.
   *
   * @return the name associated with this capability.
   */
  public String getName() {
    return name;
  }
}
