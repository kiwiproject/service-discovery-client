package org.kiwiproject.registry.consul.config;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration class to specify various Consul settings
 */
@Getter
@Setter
public class ConsulRegistrationConfig extends ConsulConfig {

    /**
     * Default number of seconds for Consul to check for health
     */
    @VisibleForTesting
    static final int DEFAULT_CHECK_INTERVAL_IN_SECONDS = 35;

    /**
     * Default number of seconds before Consul will deregister when service is unhealthy
     */
    @VisibleForTesting
    static final int DEFAULT_DEREGISTER_INTERVAL_IN_MINUTES = 1;

    /**
     * Number of seconds between Consul checking for health
     */
    private int checkIntervalInSeconds = DEFAULT_CHECK_INTERVAL_IN_SECONDS;

    /**
     * Number of seconds before Consul will deregister a service when unhealthy
     */
    private int deregisterIntervalInMinutes = DEFAULT_DEREGISTER_INTERVAL_IN_MINUTES;
}
