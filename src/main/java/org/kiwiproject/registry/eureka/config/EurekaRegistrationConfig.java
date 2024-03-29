package org.kiwiproject.registry.eureka.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration model needed for registering a service with Eureka
 */
@Getter
@Setter
public class EurekaRegistrationConfig extends EurekaConfig {

    /**
     * Default value for lease expiration (seconds).
     */
    public static final int DEFAULT_LEASE_EXPIRATION_DURATION_SECONDS = 90;

    /**
     * Maximum allowable time before lease expiration can occur (seconds).
     */
    public static final int MAX_LEASE_EXPIRATION_DURATION_SECONDS = 270;

    /**
     * Default value for heartbeats (seconds).
     */
    public static final int DEFAULT_LEASE_RENEWAL_INTERVAL_SECONDS = 30;

    /**
     * Maximum allowable time between heartbeats (seconds).
     */
    public static final int MAX_LEASE_RENEWAL_INTERVAL_SECONDS = 90;

    /**
     * The amount of time between each heartbeat to the Eureka server (in seconds)
     */
    @Min(1)
    @Max(MAX_LEASE_RENEWAL_INTERVAL_SECONDS)
    private int heartbeatIntervalInSeconds = DEFAULT_LEASE_RENEWAL_INTERVAL_SECONDS;

    /**
     * The amount of time before a service will expire from Eureka (in seconds)
     */
    @Min(1)
    @Max(MAX_LEASE_EXPIRATION_DURATION_SECONDS)
    private int expirationIntervalInSeconds = DEFAULT_LEASE_EXPIRATION_DURATION_SECONDS;

    /**
     * Whether the registry service should track the number of heartbeats sent. Mostly used for testing purposes.
     */
    private boolean trackHeartbeats;

}
