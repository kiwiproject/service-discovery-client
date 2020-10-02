package org.kiwiproject.registry.model;

import lombok.Builder;
import lombok.Getter;
import org.kiwiproject.registry.config.ServiceInfo;

import java.util.List;

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

    protected final String instanceId;
    protected final String serviceName;
    protected final String hostName;
    protected final String ip;
    protected final List<Port> ports;
    protected final ServicePaths paths;
    protected final Status status;
    protected final String commitRef;
    protected final String description;
    protected final String version;

    public static ServiceInstanceBuilder fromServiceInfo(ServiceInfo serviceInfo) {
        return ServiceInstance.builder()
                .serviceName(serviceInfo.getName())
                .hostName(serviceInfo.getHostname())
                .ip(serviceInfo.getIp())
                .ports(serviceInfo.getPorts())
                .paths(serviceInfo.getPaths())
                .commitRef(serviceInfo.getCommitRef())
                .description(serviceInfo.getDescription())
                .version(serviceInfo.getVersion());
    }

}
