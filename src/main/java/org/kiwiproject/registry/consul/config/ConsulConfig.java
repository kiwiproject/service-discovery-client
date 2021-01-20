package org.kiwiproject.registry.consul.config;

import lombok.Getter;
import lombok.Setter;
import org.kiwiproject.registry.model.ServiceInstance;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * List of keys from the {@link ServiceInstance} metadata that should become tags, otherwise they will be in metadata
     */
    private List<String> metadataTags = new ArrayList<>();

    /**
     * If true, enables the addition of Consul specific data to ServiceInstance.
     */
    private boolean includeNativeData;
}
