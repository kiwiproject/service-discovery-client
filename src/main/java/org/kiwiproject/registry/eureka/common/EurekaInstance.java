package org.kiwiproject.registry.eureka.common;

import static org.kiwiproject.base.KiwiStrings.f;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance;

import java.util.List;
import java.util.Map;

/**
 * This model matches the format of the data being sent to the Eureka server
 */
@Builder
@Value
public class EurekaInstance {

    private static final String URL_HOST_FORMAT = "{}://{}:{}{}";

    @With
    String app;

    @With
    String status;

    @With
    Map<String, String> dataCenterInfo;

    @With
    Map<String, Integer> leaseInfo;

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
        var metadataMap = Map.of(
                "commitRef", serviceInstance.getCommitRef(),
                "description", serviceInstance.getDescription(),
                "version", serviceInstance.getVersion()
        );

        var hostName = serviceInstance.getHostName();
        var ports = serviceInstance.getPorts();
        var paths = serviceInstance.getPaths();

        return EurekaInstance.builder()
                .hostName(hostName)
                .ipAddr(serviceInstance.getIp())
                .vipAddress(serviceInstance.getServiceName())
                .secureVipAddress(serviceInstance.getServiceName())
                .status(serviceInstance.getStatus().name())
                .port(portToEurekaPortMap(findPort(ports, Port.Security.NOT_SECURE, Port.PortType.APPLICATION)))
                .securePort(portToEurekaPortMap(findPort(ports, Port.Security.SECURE, Port.PortType.APPLICATION)))
                .adminPort(findFirstAdminPortNumberPreferSecure(ports))
                .homePageUrl(urlForPath(hostName, ports, Port.PortType.APPLICATION, paths.getHomePagePath()))
                .statusPageUrl(urlForPath(hostName, ports, Port.PortType.ADMIN, paths.getStatusPath()))
                .healthCheckUrl(urlForPath(hostName, ports, Port.PortType.ADMIN, paths.getHealthCheckPath()))
                .metadata(metadataMap)
                .build();

    }

    private static Port findPort(List<Port> ports, Port.Security security, Port.PortType type) {
        return ports.stream()
                .filter(p -> p.getSecure() == security)
                .filter(p -> p.getType() == type)
                .findFirst()
                .orElseGet(() -> Port.builder().number(0).secure(security).type(type).build());
    }

    private static Map<String, Object> portToEurekaPortMap(Port port) {
        return Map.of(
                "$", port.getNumber(),
                "@enabled", (port.getNumber() > 0)
        );
    }

    private static int findFirstAdminPortNumberPreferSecure(List<Port> ports) {
        var securePort = findPort(ports, Port.Security.SECURE, Port.PortType.ADMIN);

        if (securePort.getNumber() > 0) {
            return securePort.getNumber();
        }

        return findPort(ports, Port.Security.NOT_SECURE, Port.PortType.ADMIN).getNumber();
    }

    private static Port findFirstPortPreferSecure(List<Port> ports, Port.PortType type) {
        var securePort = findPort(ports, Port.Security.SECURE, type);
        if (securePort.getNumber() > 0) {
            return securePort;
        }

        return findPort(ports, Port.Security.NOT_SECURE, type);
    }

    private static String urlForPath(String hostName, List<Port> ports, Port.PortType type, String path) {
        var port = findFirstPortPreferSecure(ports, type);

        var protocol = port.getSecure() == Port.Security.SECURE ? "https" : "http";
        return f(URL_HOST_FORMAT, protocol, hostName, port.getNumber(), path);
    }
}
