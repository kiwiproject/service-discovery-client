package org.kiwiproject.registry.model;

import static java.util.Objects.isNull;

import lombok.Builder;
import lombok.Getter;
import lombok.With;
import org.kiwiproject.registry.config.ServiceInfo;

import java.time.Instant;
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
    private final Instant upSince;

    /**
     * Used to store extra data in a discovery service for this instance
     */
    @With
    private final Map<String, String> metadata;

    /**
     * Used to store native registry data that includes data mapped into {@link ServiceInstance} as well as any
     * additional information that is not mapped. This will only be populated if the registry configuration
     * specifies to include native data.
     */
    @With
    private final Map<String, Object> nativeRegistryData;

    /**
     * Returns a new {@code ServiceInstanceBuilder} built from a given {@link ServiceInfo}.
     * <p>
     * Note that a copy of {@link ServiceInfo#getMetadata()} is made using {@link Map#copyOf(Map)}, so the metadata
     * is unmodifiable.
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
                .metadata(Map.copyOf(serviceInfo.getMetadata()))
                .build();
    }

    /**
     * Returns the Instant this service has been up since, or {@link Instant#EPOCH} if no value was provided
     * when this instance was created.
     *
     * @return a non-null Instant representing the instant this service was started, or the epoch if no value provided
     */
    public Instant getUpSince() {
        return isNull(upSince) ? Instant.EPOCH : upSince;
    }

    /**
     * Returns the time since the epoch this service has been up since, or <em>zero</em> (the epoch), if no value
     * was provided when this instance was created.
     *
     * @return the number of milliseconds since the epoch that this service was started, or zero if no value provided
     */
    public long getUpSinceMillis() {
        return getUpSince().toEpochMilli();
    }

}
