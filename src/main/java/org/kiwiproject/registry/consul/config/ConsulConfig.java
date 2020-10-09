package org.kiwiproject.registry.consul.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Base configuration class for Consul registry usage
 */
@Getter
@Setter
public class ConsulConfig {

    /**
     * Specifies a domain to use for service addresses if needed
     */
    private String domainOverride;

}
