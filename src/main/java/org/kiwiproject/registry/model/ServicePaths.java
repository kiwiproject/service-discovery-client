package org.kiwiproject.registry.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Model defining various known paths used by services
 */
@Builder
@Getter
@SuppressWarnings({"java:S1075", "FieldMayBeFinal"})
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
    private String homePagePath = DEFAULT_HOMEPAGE_PATH;

    @Builder.Default
    private String statusPath = DEFAULT_STATUS_PATH;

    @Builder.Default
    private String healthCheckPath = DEFAULT_HEALTHCHECK_PATH;

}
