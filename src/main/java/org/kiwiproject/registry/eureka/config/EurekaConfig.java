package org.kiwiproject.registry.eureka.config;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.kiwiproject.net.KiwiUrls.replaceDomainsIn;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Getter
@Setter
@Slf4j
public class EurekaConfig {

    /**
     * A comma separated list of urls pointing to Eureka servers.
     * <p>
     * In YAML or JSON configuration, this supports specifying as a CSV string or as a list/array of strings.
     * <p>
     * For example in YAML as a CSV string.
     *
     * <pre>
     * registryUrls: eureka-url-1,eureka-url-2
     * </pre>
     * <p>
     * Or as a list/array (also YAML):
     *
     * <pre>
     * registryUrls:
     *   - eureka-url-1
     *   - eureka-url-2
     * </pre>
     */
    @JsonDeserialize(using = ListToCsvStringDeserializer.class)
    private String registryUrls;

    /**
     * Allows for adjusting {@code registryUrls} domain at runtime. This is useful if the urls are constant across services
     * but a single service needs to access the server on a different domain due to networking restrictions
     */
    private String domainOverride;

    /**
     * If true, enables the addition of Eureka specific data to ServiceInstance.
     */
    private boolean includeNativeData;

    /**
     * @return comma separated list of urls pointing to Eureka servers, with domains replaced if {@code domainOverride}
     * is set
     */
    @NotBlank
    public String getRegistryUrls() {
        var adjustedUrls = isBlank(domainOverride) ? registryUrls : replaceDomainsIn(registryUrls, domainOverride);
        logWarningIfDomainOverrideIsSet();
        return adjustedUrls;
    }

    /**
     * Set the comma-separate list of Eureka server URLs.
     *
     * @param urlCsv a string containing the CSV string containing Eureka server URLs
     */
    public void setRegistryUrls(String urlCsv) {
        this.registryUrls = urlCsv;
    }

    /**
     * Convenience method to set Eureka server URLS from a list of URLs rather than a CSV string.
     *
     * @param urls the list of URLs to set
     */
    public void setRegistryUrls(List<String> urls) {
        this.registryUrls = String.join(",", urls);
    }

    private void logWarningIfDomainOverrideIsSet() {
        if (isNotBlank(domainOverride)) {
            LOG.warn("The 'domainOverride' parameter supersedes 'registryUrls'. Both are currently set in YAML config");
        }
    }

}
