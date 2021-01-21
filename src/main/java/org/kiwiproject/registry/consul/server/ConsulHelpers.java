package org.kiwiproject.registry.consul.server;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.registry.util.Ports.findFirstPortPreferSecure;

import com.google.common.annotations.VisibleForTesting;
import lombok.experimental.UtilityClass;
import org.kiwiproject.base.DefaultEnvironment;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.ServiceInstance;

import java.util.HashMap;
import java.util.Map;

/**
 * Set of utilities that assist in populating a service registration.
 */
@UtilityClass
public class ConsulHelpers {

    /**
     * Creates a map with default metadata items that the {@link org.kiwiproject.registry.consul.client.ConsulRegistryClient}
     * will be looking for when it converts a registered service back into a {@link org.kiwiproject.registry.model.ServiceInstance}.
     * <p>
     * This map is intended to be a baseline of metadata to register.  This map is modifiable so it can easily be
     * changed or added to later in the registration process.
     *
     * @param serviceInfo the information about the service to pull data from.
     * @return a map that is prepopulated with defaults.
     */
    public static Map<String, String> buildDefaultMetadataMap(ServiceInfo serviceInfo) {
        return buildDefaultMetadataMap(ServiceInstance.fromServiceInfo(serviceInfo), new DefaultEnvironment());
    }

    @VisibleForTesting
    static Map<String, String> buildDefaultMetadataMap(ServiceInfo serviceInfo, KiwiEnvironment kiwiEnvironment) {
        return buildDefaultMetadataMap(ServiceInstance.fromServiceInfo(serviceInfo), kiwiEnvironment);
    }

    /**
     * Creates a map with default metadata items that the {@link org.kiwiproject.registry.consul.client.ConsulRegistryClient}
     * will be looking for when it converts a registered service back into a {@link org.kiwiproject.registry.model.ServiceInstance}.
     * <p>
     * This map is intended to be a baseline of metadata to register.  This map is modifiable so it can easily be
     * changed or added to later in the registration process.
     *
     * @param serviceInstance the information about the service to pull data from.
     * @return a map that is prepopulated with defaults.
     */
    public static Map<String, String> buildDefaultMetadataMap(ServiceInstance serviceInstance) {
        return buildDefaultMetadataMap(serviceInstance, new DefaultEnvironment());
    }

    @VisibleForTesting
    static Map<String, String> buildDefaultMetadataMap(ServiceInstance serviceInstance, KiwiEnvironment kiwiEnvironment) {
        checkArgumentNotNull(serviceInstance);

        var metadata = new HashMap<String, String>();

        var ports = serviceInstance.getPorts();
        metadata.put("scheme", findFirstPortPreferSecure(ports, PortType.APPLICATION).getScheme());
        metadata.put("adminPort", String.valueOf(findFirstPortPreferSecure(ports, PortType.ADMIN).getNumber()));

        metadata.put("serviceUpTimestamp", String.valueOf(kiwiEnvironment.currentTimeMillis()));

        var paths = serviceInstance.getPaths();
        metadata.put("homePagePath", paths.getHomePagePath());
        metadata.put("statusPath", paths.getStatusPath());
        metadata.put("healthCheckPath", paths.getHealthCheckPath());

        metadata.put("commitRef", serviceInstance.getCommitRef());
        metadata.put("description", serviceInstance.getDescription());
        metadata.put("version", serviceInstance.getVersion());
        metadata.put("ipAddress", serviceInstance.getIp());

        return metadata;
    }
}
