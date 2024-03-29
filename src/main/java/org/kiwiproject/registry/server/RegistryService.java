package org.kiwiproject.registry.server;

import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.model.ServiceInstance;

/**
 * Contract for implementations of a service registry.
 */
public interface RegistryService {

    /**
     * Creates a new {@link ServiceInstance} instance from a {@link ServiceInfo} that can be registered with the registry server.
     *
     * @param serviceInfo Service information that will be used to set up a new instance to be registered.
     * @return The {@link ServiceInstance} object that was created
     */
    ServiceInstance createCandidateFrom(ServiceInfo serviceInfo);

    /**
     * Registers a {@link ServiceInstance} with the registry server and returns a new instance containing information about the
     * registered instance.
     *
     * @param serviceToRegister The service information to register
     * @return The service instance that was registered
     */
    ServiceInstance register(ServiceInstance serviceToRegister);

    /**
     * Sends an update to the registry server to inform of a service status change
     *
     * @param newStatus         The new status that will be sent
     * @return a copy of the original {@link ServiceInstance} with the new status set
     */
    ServiceInstance updateStatus(ServiceInstance.Status newStatus);

    /**
     * Unregisters any registrations that were previously registered. This should be a NO-OP if nothing has ever been registered.
     */
    void unregister();
}
