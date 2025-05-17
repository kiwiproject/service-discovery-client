package org.kiwiproject.registry.eureka.common;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.kiwiproject.base.KiwiObjects.firstNonNullOrNull;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

import com.google.common.annotations.VisibleForTesting;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.net.KiwiInternetAddresses;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@UtilityClass
public class EurekaResponseParser {

    private static final String INSTANCE_KEY = "instance";

    @SuppressWarnings("unchecked")
    public static List<EurekaInstance> parseEurekaApplicationsResponse(Map<String, Object> eurekaResponse) {
        checkArgumentNotNull(eurekaResponse, "Eureka applications response must not be null");

        var applications = (Map<String, Object>) eurekaResponse.get("applications");

        checkState(nonNull(applications), "Eureka data must contain a key 'applications' that contains a Map<String, Object>");

        var applicationOrApplicationList = applications.get("application");

        var eurekaInstances = new ArrayList<EurekaInstance>();

        if (nonNull(applicationOrApplicationList)) {
            if (applicationOrApplicationList instanceof List<?>) {
                for (Map<String, Object> application : (List<Map<String, Object>>) applicationOrApplicationList) {
                    var instances = application.get(INSTANCE_KEY);
                    eurekaInstances.addAll(parseInstances(instances));
                }
            } else {
                eurekaInstances.addAll(parseInstances(((Map<String, Object>) applicationOrApplicationList).get(INSTANCE_KEY)));
            }
        }

        return eurekaInstances;
    }

    @SuppressWarnings("unchecked")
    private static List<EurekaInstance> parseInstances(Object instanceOrInstanceList) {
        checkArgumentNotNull(instanceOrInstanceList, "Instance data from Eureka can not be null");

        if (instanceOrInstanceList instanceof List<?>) {
            var instanceList = (List<Map<String, Object>>) instanceOrInstanceList;
            return instanceList.stream()
                    .map(EurekaResponseParser::buildInstance)
                    .toList();
        }

        var instance = (Map<String, Object>) instanceOrInstanceList;
        return newArrayList(buildInstance(instance));
    }

    public static EurekaInstance parseEurekaInstanceResponse(Map<String, Object> eurekaResponse) {
        checkArgumentNotNull(eurekaResponse, "Eureka instance response must not be null");

        var instanceObj = eurekaResponse.get(INSTANCE_KEY);
        checkState(instanceObj instanceof Map, "Eureka data must contain a key 'instance' that contains a Map<String, Object>");

        //noinspection unchecked
        var instance = (Map<String, Object>) instanceObj;

        return buildInstance(instance);
    }

    @SuppressWarnings("unchecked")
    private static EurekaInstance buildInstance(Map<String, Object> instanceData) {

        var portMap = (Map<String, Object>) instanceData.get("port");
        var securePortMap = (Map<String, Object>) instanceData.get("securePort");
        var metadataMap = (Map<String, String>) instanceData.get("metadata");
        var leaseInfoMap = (Map<String, Object>) instanceData.get("leaseInfo");

        Map<String, Object> leaseInfo = extractLeaseInfo(leaseInfoMap);

        var statusUrl = getStringOrNull(instanceData, "statusPageUrl");
        var healthCheckUrl = getStringOrNull(instanceData, "healthCheckUrl");
        var url = firstNonNullOrNull(statusUrl, healthCheckUrl);
        var adminPort = getAdminPort(url);

        return EurekaInstance.builder()
                .vipAddress(getStringOrNull(instanceData, "vipAddress"))
                .secureVipAddress(getStringOrNull(instanceData, "vipAddress"))
                .app(getStringOrNull(instanceData, "app"))
                .hostName(getStringOrNull(instanceData, "hostName"))
                .ipAddr(getStringOrNull(instanceData, "ipAddr"))
                .status(getStringOrNull(instanceData, "status"))
                .homePageUrl(getStringOrNull(instanceData, "homePageUrl"))
                .healthCheckUrl(healthCheckUrl)
                .statusPageUrl(statusUrl)
                .port(portMap)
                .securePort(securePortMap)
                .adminPort(adminPort)
                .leaseInfo(leaseInfo)
                .metadata(metadataMap)
                .rawResponse(instanceData)
                .build();
    }

    private static Map<String, Object> extractLeaseInfo(Map<String, Object> leaseInfoMap) {
        if (nonNull(leaseInfoMap)) {
            return Map.of(
                    "renewalIntervalInSecs", leaseInfoMap.getOrDefault("renewalIntervalInSecs", 0),
                    "durationInSecs", leaseInfoMap.getOrDefault("durationInSecs", 0),
                    "registrationTimestamp", leaseInfoMap.getOrDefault("registrationTimestamp", 0L),
                    "lastRenewalTimestamp", leaseInfoMap.getOrDefault("lastRenewalTimestamp", 0L),
                    "evictionTimestamp", leaseInfoMap.getOrDefault("evictionTimestamp", 0L),
                    "serviceUpTimestamp", leaseInfoMap.getOrDefault("serviceUpTimestamp", 0L));
        }

        return Map.of();
    }

    private static <K> String getStringOrNull(final Map<? super K, ?> map, final K key) {
        var value = map.getOrDefault(key, null);

        verify(isNull(value) || value instanceof String, "Value from Map must be a string or null");

        return (String) value;
    }

    @VisibleForTesting
    static int getAdminPort(@Nullable String url) {
        if (isBlank(url)) {
            return 0;
        }

        return KiwiInternetAddresses.portFrom(url).orElseGet(() -> defaultPortForScheme(url));
    }

    private static int defaultPortForScheme(String url) {
        return url.startsWith("https") ? 443 : 80;
    }
}
