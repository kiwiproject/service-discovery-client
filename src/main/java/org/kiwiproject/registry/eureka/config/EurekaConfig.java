package org.kiwiproject.registry.eureka.config;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.net.KiwiUrls.replaceDomainsIn;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.kiwiproject.jackson.ser.ListToCsvStringDeserializer;
import org.slf4j.event.Level;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base configuration class for Eureka registry client configuration.
 */
@Getter
@Setter
@Slf4j
public class EurekaConfig {

    private static final String DEFAULT_RETRY_ID_PREFIX = "EurekaRegistryClient-";

    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger(0);

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
     * A unique ID that will be used when logging HTTP call attempts to Eureka.
     * <p>
     * The default is "EurekaRegistryClient-" plus a unique
     * integer, e.g. "EurekaRegistryClient-1", "EurekaRegistryClient-2", etc.
     */
    private String retryId = retryId(INSTANCE_COUNT.incrementAndGet());

    /**
     * The log level to use when logging HTTP call attempts. The default is DEBUG.
     */
    private Level retryProcessingLogLevel = Level.DEBUG;

    /**
     * The log level to use when logging HTTP call attempts that fail with an exception.
     * The default is WARN.
     */
    private Level retryExceptionLogLevel = Level.WARN;

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

    /**
     * Sets the retry ID to {@code "EurekaRegistryClient-" + id}.
     *
     * @param id a unique identifier
     * @see #retryId(Object)
     */
    public void setRetryId(String id) {
        this.retryId = retryId(id);
    }

    /**
     * Generates a {@code retryId} by appending the String value of {@code identifier} to "EurekaRegistryClient-".
     *
     * @param identifier the unique identifier to use
     * @return a new retry identifier
     * @implNote if {@code identifier} as a String starts with "EurekaRegistryClient-", that value returned as-is
     */
    public static String retryId(Object identifier) {
        checkArgumentNotNull(identifier, "identifier must not be null");

        var idString = identifier.toString();
        if (idString.startsWith(DEFAULT_RETRY_ID_PREFIX)) {
            return idString;
        }

        return DEFAULT_RETRY_ID_PREFIX + idString;
    }

}
