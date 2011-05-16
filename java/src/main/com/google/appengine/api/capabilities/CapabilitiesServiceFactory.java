package com.google.appengine.api.capabilities;

/**
 * Factory for creating a {@link CapabilitiesService}.
 *
 */
public class CapabilitiesServiceFactory {

  /**
   * Creates an implementation of the {@link CapabilitiesService}.
   *
   * @return an instance of the capabilities service.
   */
  public static CapabilitiesService getCapabilitiesService() {
    return new CapabilitiesServiceImpl();
  }

}
