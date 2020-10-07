package org.kiwiproject.registry.eureka.client;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

import lombok.experimental.UtilityClass;
import org.kiwiproject.registry.eureka.common.EurekaInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@UtilityClass
class EurekaParser {

    @SuppressWarnings("unchecked")
    public static List<EurekaInstance> parseEurekaResponse(Map<String, Object> response) {
        checkArgumentNotNull(response, "Eureka response map cannot be null");

        var applications = (Map<String, Object>) response.get("applications");

        checkState(nonNull(applications), "Eureka data must contain a key 'applications' that contains a Map<String, Object>");

        var applicationOrApplicationList = applications.get("application");

        var eurekaInstances = new ArrayList<EurekaInstance>();

        if (nonNull(applicationOrApplicationList)) {
            if (applicationOrApplicationList instanceof List<?>) {
                for (Map<String, Object> application : (List<Map<String, Object>>) applicationOrApplicationList) {
                    var instances = application.get("instance");
                    eurekaInstances.addAll(parseInstances(instances));
                }
            } else {
                eurekaInstances.addAll(parseInstances(((Map<String, Object>) applicationOrApplicationList).get("instance")));
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
                    .map(EurekaParser::buildInstance)
                    .collect(toList());
        }

        var instance = (Map<String, Object>) instanceOrInstanceList;
        return newArrayList(buildInstance(instance));
    }

    @SuppressWarnings("unchecked")
    private static EurekaInstance buildInstance(Map<String, Object> instanceData) {

        var portMap = (Map<String, Object>) instanceData.get("port");
        var securePortMap = (Map<String, Object>) instanceData.get("securePort");
        var metadataMap = (Map<String, String>) instanceData.get("metadata");
        var leaseInfoMap = (Map<String, Object>) instanceData.get("leaseInfo");

        Map<String, Object> leaseInfo;
        if (nonNull(leaseInfoMap)) {
            leaseInfo = Map.of(
                    "renewalIntervalInSecs", leaseInfoMap.getOrDefault("renewalIntervalInSecs", 0),
                    "durationInSecs", leaseInfoMap.getOrDefault("durationInSecs", 0),
                    "registrationTimestamp", leaseInfoMap.getOrDefault("registrationTimestamp", 0L),
                    "lastRenewalTimestamp", leaseInfoMap.getOrDefault("lastRenewalTimestamp", 0L),
                    "evictionTimestamp", leaseInfoMap.getOrDefault("evictionTimestamp", 0L),
                    "serviceUpTimestamp", leaseInfoMap.getOrDefault("serviceUpTimestamp", 0L));
        } else {
            leaseInfo = Map.of();
        }

        return EurekaInstance.builder()
                .vipAddress(getString(instanceData, "vipAddress"))
                .secureVipAddress(getString(instanceData, "vipAddress"))
                .app(getString(instanceData, "app"))
                .hostName(getString(instanceData, "hostName"))
                .ipAddr(getString(instanceData, "ipAddr"))
                .status(getString(instanceData, "status"))
                .homePageUrl(getString(instanceData, "homePageUrl"))
                .healthCheckUrl(getString(instanceData, "healthCheckUrl"))
                .statusPageUrl(getString(instanceData, "statusUrl"))
                .port(portMap)
                .securePort(securePortMap)
                .leaseInfo(leaseInfo)
                .metadata(metadataMap)
                .build();
    }

    @SuppressWarnings("ConstantConditions")
    private static <K> String getString(final Map<? super K, ?> map, final K key) {
        var value = map.getOrDefault(key, null);

        verify(isNull(value) || value instanceof String, "Value from Map must be a string or null");

        return (String) value;
    }

}
