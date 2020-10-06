package org.kiwiproject.registry.eureka.client;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.MapUtils.getString;

import lombok.experimental.UtilityClass;
import org.kiwiproject.registry.eureka.common.EurekaInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@UtilityClass
public class EurekaParser {

    @SuppressWarnings("unchecked")
    public static List<EurekaInstance> parseEurekaResponse(Map<String, Object> response) {
        var applications = ((Map<String, Object>) response.get("applications")).get("application");

        var eurekaInstances = new ArrayList<EurekaInstance>();

        if (nonNull(applications)) {
            if (applications instanceof List<?>) {
                for (Map<String, Object> application : (List<Map<String, Object>>) applications) {
                    var instances = application.get("instance");
                    eurekaInstances.addAll(parseInstances(instances));
                }
            } else {
                eurekaInstances.addAll(parseInstances(((Map<String, Object>) applications).get("instance")));
            }
        }

        return eurekaInstances;
    }

    @SuppressWarnings("unchecked")
    private static List<EurekaInstance> parseInstances(Object instanceOrInstanceList) {
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
    public static EurekaInstance buildInstance(Map<String, Object> instanceData) {

        var portMap = (Map<String, Object>) instanceData.get("port");
        var securePortMap = (Map<String, Object>) instanceData.get("securePort");
        var metadataMap = (Map<String, String>) instanceData.get("metadata");
        var leaseInfoMap = (Map<String, Object>) instanceData.get("leaseInfo");

        var leaseInfo = Map.of(
                "renewalIntervalInSecs", leaseInfoMap.getOrDefault("renewalIntervalInSecs", 0),
                "durationInSecs", leaseInfoMap.getOrDefault("durationInSecs", 0),
                "registrationTimestamp", leaseInfoMap.getOrDefault("registrationTimestamp", 0L),
                "lastRenewalTimestamp", leaseInfoMap.getOrDefault("lastRenewalTimestamp", 0L),
                "evictionTimestamp", leaseInfoMap.getOrDefault("evictionTimestamp", 0L),
                "serviceUpTimestamp", leaseInfoMap.getOrDefault("serviceUpTimestamp", 0L));

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

}
