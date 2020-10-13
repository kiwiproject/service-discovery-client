package org.kiwiproject.registry.eureka.common;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.kiwiproject.collect.KiwiMaps.isNullOrEmpty;
import static org.kiwiproject.registry.model.Port.Security.NOT_SECURE;
import static org.kiwiproject.registry.model.Port.Security.SECURE;
import static org.kiwiproject.registry.util.Ports.findFirstPortPreferSecure;
import static org.kiwiproject.registry.util.Ports.findPort;
import static org.kiwiproject.registry.util.ServiceInstancePaths.urlForPath;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.model.ServicePaths;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * This model matches the format of the data being sent to the Eureka server
 */
@Builder
@Value
@JsonIgnoreProperties(ignoreUnknown = true)
public class EurekaInstance {

    private static final String COMMIT_REF_FIELD = "commitRef";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String VERSION_FIELD = "version";

    private static final List<String> METADATA_EXCLUDED_KEYS = List.of(COMMIT_REF_FIELD, DESCRIPTION_FIELD, VERSION_FIELD);

    @With
    String app;

    @With
    String status;

    @With
    Map<String, String> dataCenterInfo;

    @With
    Map<String, Object> leaseInfo;

    String hostName;
    String ipAddr;
    String vipAddress;
    String secureVipAddress;
    Map<String, Object> port;
    Map<String, Object> securePort;
    int adminPort;
    String homePageUrl;
    String statusPageUrl;
    String healthCheckUrl;
    Map<String, String> metadata;

    public String getInstanceId() {
        return hostName;
    }

    public static EurekaInstance fromServiceInstance(ServiceInstance serviceInstance) {

        var hostName = serviceInstance.getHostName();
        var ports = serviceInstance.getPorts();
        var paths = serviceInstance.getPaths();

        return EurekaInstance.builder()
                .hostName(hostName)
                .ipAddr(serviceInstance.getIp())
                .vipAddress(serviceInstance.getServiceName())
                .secureVipAddress(serviceInstance.getServiceName())
                .status(serviceInstance.getStatus().name())
                .port(portToEurekaPortMap(findPort(ports, NOT_SECURE, Port.PortType.APPLICATION)))
                .securePort(portToEurekaPortMap(findPort(ports, Port.Security.SECURE, Port.PortType.APPLICATION)))
                .adminPort(findFirstAdminPortNumberPreferSecure(ports))
                .homePageUrl(urlForPath(hostName, ports, Port.PortType.APPLICATION, paths.getHomePagePath()))
                .statusPageUrl(urlForPath(hostName, ports, Port.PortType.ADMIN, paths.getStatusPath()))
                .healthCheckUrl(urlForPath(hostName, ports, Port.PortType.ADMIN, paths.getHealthCheckPath()))
                .metadata(mergeMetadata(serviceInstance))
                .build();

    }

    private static Map<String, String> mergeMetadata(ServiceInstance serviceInstance) {
        var defaultMetadataMap = Map.of(
                COMMIT_REF_FIELD, serviceInstance.getCommitRef(),
                DESCRIPTION_FIELD, serviceInstance.getDescription(),
                VERSION_FIELD, serviceInstance.getVersion()
        );

        if (isNullOrEmpty(serviceInstance.getMetadata())) {
            return defaultMetadataMap;
        }

        return Stream.concat(defaultMetadataMap.entrySet().stream(), serviceInstance.getMetadata().entrySet().stream())
                .collect(toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (value1, value2) -> value2
                ));
    }

    private static Map<String, Object> portToEurekaPortMap(Port port) {
        return Map.of(
                "$", port.getNumber(),
                "@enabled", (port.getNumber() > 0)
        );
    }

    private static int findFirstAdminPortNumberPreferSecure(List<Port> ports) {
        var firstAdminPort = findFirstPortPreferSecure(ports, Port.PortType.ADMIN);

        return firstAdminPort.getNumber();
    }

    public ServiceInstance toServiceInstance() {
        var ports = portListFromPortsIgnoringNulls(buildAdminPort(), buildApplicationPort(port, NOT_SECURE),
                buildApplicationPort(securePort, SECURE));

        return ServiceInstance.builder()
                .instanceId(getInstanceId())
                .status(ServiceInstance.Status.valueOf(status))
                .serviceName(vipAddress)
                .hostName(hostName)
                .ip(ipAddr)
                .commitRef(metadata.get(COMMIT_REF_FIELD))
                .description(metadata.get(DESCRIPTION_FIELD))
                .version(metadata.get(VERSION_FIELD))
                .metadata(filterMetadata())
                .paths(buildPaths())
                .ports(ports)
                .build();
    }

    private Map<String, String> filterMetadata() {
        return metadata.entrySet().stream()
                .filter(entry -> !METADATA_EXCLUDED_KEYS.contains(entry.getKey()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<Port> portListFromPortsIgnoringNulls(Port...ports) {
        return Arrays.stream(ports)
                .filter(Objects::nonNull)
                .collect(toUnmodifiableList());
    }

    private Port buildAdminPort() {
        if (adminPort == 0) {
            return null;
        }

        var secure = isNull(statusPageUrl) || statusPageUrl.startsWith("https") ? Port.Security.SECURE : NOT_SECURE;

        return Port.builder()
                .number(adminPort)
                .secure(secure)
                .type(Port.PortType.ADMIN)
                .build();
    }

    private Port buildApplicationPort(Map<String, Object> portDef, Port.Security secure) {
        if (!isEnabled(portDef.get("@enabled"))) {
            return null;
        }

        return Port.builder()
                .number((int) portDef.get("$"))
                .type(Port.PortType.APPLICATION)
                .secure(secure)
                .build();
    }

    private boolean isEnabled(Object enabledFlag) {
        if (enabledFlag instanceof String) {
            return Boolean.parseBoolean((String) enabledFlag);
        }

        return (boolean) enabledFlag;
    }

    private ServicePaths buildPaths() {
        return ServicePaths.builder()
                .homePagePath(extractPathOrDefault(homePageUrl, ServicePaths.DEFAULT_HOMEPAGE_PATH))
                .statusPath(extractPathOrDefault(statusPageUrl, ServicePaths.DEFAULT_STATUS_PATH))
                .healthCheckPath(extractPathOrDefault(healthCheckUrl, ServicePaths.DEFAULT_HEALTHCHECK_PATH))
                .build();
    }

    private String extractPathOrDefault(String url, String defaultPath) {
        try {
            return new URL(url).getPath();
        } catch (MalformedURLException e) {
            return defaultPath;
        }
    }
}
