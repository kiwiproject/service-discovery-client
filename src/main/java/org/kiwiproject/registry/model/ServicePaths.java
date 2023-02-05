package org.kiwiproject.registry.model;

import lombok.Builder;
import lombok.Value;

/**
 * Model defining various known paths used by services
 */
@Value
@Builder
@SuppressWarnings({"java:S1075"})  // Sonar S1075: URIs should not be hardcoded
public class ServicePaths {

    /**
     * The default path to the main API of the service.
     */
    public static final String DEFAULT_HOMEPAGE_PATH = "";

    /**
     * The default path to a status page for the service.
     */
    public static final String DEFAULT_STATUS_PATH = "/ping";

    /**
     * The default path to a health check page for the service.
     */
    public static final String DEFAULT_HEALTHCHECK_PATH = "/healthcheck";

    @Builder.Default
    String homePagePath = DEFAULT_HOMEPAGE_PATH;

    @Builder.Default
    String statusPath = DEFAULT_STATUS_PATH;

    @Builder.Default
    String healthCheckPath = DEFAULT_HEALTHCHECK_PATH;

}
