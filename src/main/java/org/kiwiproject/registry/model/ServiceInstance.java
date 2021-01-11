package org.kiwiproject.registry.model;

import lombok.Builder;
import lombok.Getter;
import lombok.With;
import org.kiwiproject.registry.config.ServiceInfo;

import java.util.List;
import java.util.Map;

/**
 * Model containing information about a running service
 */
@Getter
@Builder(toBuilder = true)
public class ServiceInstance {

    /**
     * The status of a service
     */
    public enum Status {
        UP,
        DOWN,
        STARTING
    }

    @With
    private final String instanceId;

    @With
    private final Status status;

    private final String serviceName;
    private final String hostName;
    private final String ip;
    private final List<Port> ports;
    private final ServicePaths paths;
    private final String commitRef;
    private final String description;
    private final String version;

    /**
     * Used to store extra data in a discovery service for this instance
     */
    @With
    private final Map<String, String> metadata;

    /**
     * Returns a new {@code ServiceInstanceBuilder} built from a given {@link ServiceInfo}
     *
     * @param serviceInfo The information about the service used to initialize the {@code ServiceInstanceBuilder}
     * @return a {@code ServiceInstanceBuilder} with values copied from the given {@link ServiceInfo}
     */
    public static ServiceInstance fromServiceInfo(ServiceInfo serviceInfo) {
        return ServiceInstance.builder()
                .serviceName(serviceInfo.getName())
                .hostName(serviceInfo.getHostname())
                .ip(serviceInfo.getIp())
                .ports(serviceInfo.getPorts())
                .paths(serviceInfo.getPaths())
                .commitRef(serviceInfo.getCommitRef())
                .description(serviceInfo.getDescription())
                .version(serviceInfo.getVersion())
                .metadata(serviceInfo.getMetadata())
                .build();
    }

}
