package org.kiwiproject.registry.consul.config;

import lombok.Getter;
import lombok.Setter;

import org.kiwiproject.base.KiwiDeprecated;
import org.kiwiproject.base.KiwiDeprecated.Severity;
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
     *
     * @deprecated without replacement; to be removed in 2.0.0
     */
    @Deprecated(since = "1.1.9", forRemoval = true)
    @KiwiDeprecated(removeAt = "2.0.0",
                    usageSeverity = Severity.SEVERE,
                    reference = "https://github.com/kiwiproject/service-discovery-client/issues/268")
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
