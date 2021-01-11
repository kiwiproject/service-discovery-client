package org.kiwiproject.registry.config;

import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServicePaths;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interface to assist in providing information about a service for registering.
 */
public interface ServiceInfo {

    /**
     * Returns a name for the service. This name will be used when registering the service.
     *
     * @return The name for the service.
     */
    String getName();

    /**
     * Returns a human readable version of the service name. Defaults to {@link #getName()}.
     *
     * @return A human readable version of the name or the name itself by default
     */
    default String humanReadableName() {
        return getName();
    }

    /**
     * Returns the hostname that the service is running on.
     *
     * @return The hostname for the service
     */
    String getHostname();

    /**
     * Returns the IP Address that the service is running on.
     *
     * @return The ip address for the service
     */
    String getIp();

    /**
     * Returns a list of port definitions that are being used by the service
     *
     * @return a list of ports
     * @see Port
     */
    List<Port> getPorts();

    /**
     * Returns the various paths common paths needed for service management. Defaults to standard paths.
     *
     * @return The paths defined in the service for management purposes
     * @see ServicePaths
     */
    default ServicePaths getPaths() {
        return ServicePaths.builder().build();
    }

    /**
     * Returns the description of the service. Defaults to empty string.
     *
     * @return A human readable description of the service
     */
    default String getDescription() {
        return "";
    }

    /**
     * Returns the version of the running service.
     *
     * @return the service version
     */
    String getVersion();

    /**
     * Returns the commit reference of the service. This can be used to know exactly what point in the source control
     * a running service is running.
     *
     * @return The commit point of the running service
     */
    default String getCommitRef() {
        return "Unknown";
    }

    /**
     * Returns metadata for the service. This is generally used to supply custom information about a service to a
     * service registry.
     *
     * @return a map containing custom metadata. This default implementation returns an empty map.
     */
    default Map<String, String> getMetadata() {
        return new HashMap<>();
    }

}
