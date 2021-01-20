package org.kiwiproject.registry.eureka.config;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.kiwiproject.net.KiwiUrls.replaceDomainsIn;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@Slf4j
public class EurekaConfig {

    /**
     * A comma separated list of urls pointing to Eureka servers
     */
    protected String registryUrls;

    /**
     * Allows for adjusting {@code registryUrls} domain at runtime. This is useful if the urls are constant across services
     * but a single service needs to access the server on a different domain due to networking restrictions
     */
    private String domainOverride;

    /**
     * If true, enables the addition of Eureka specific data to ServiceInstance.
     */
    private boolean includeNativeData;

    @NotBlank
    public String getRegistryUrls() {
        var adjustedUrls = isBlank(domainOverride) ? registryUrls : replaceDomainsIn(registryUrls, domainOverride);
        logWarningIfDomainOverrideIsSet();
        return adjustedUrls;
    }

    private void logWarningIfDomainOverrideIsSet() {
        if (isNotBlank(domainOverride)) {
            LOG.warn("The 'domainOverride' parameter supersedes 'registryUrls'. Both are currently set in YAML config");
        }
    }

}
