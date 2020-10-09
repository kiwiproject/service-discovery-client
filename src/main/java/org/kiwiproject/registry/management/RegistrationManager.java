package org.kiwiproject.registry.management;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.server.RegistryService;

/**
 * Manager to register and unregister a service.  This class can be used in a stand-alone manner or by adding it to a lifecycle listener for the
 * service.
 */
@Slf4j
public class RegistrationManager {

    @Getter
    private final ServiceInfo serviceInfo;

    private final RegistryService registryService;

    public RegistrationManager(ServiceInfo serviceInfo, RegistryService registryService) {
        this.serviceInfo = serviceInfo;
        this.registryService = registryService;
    }

    /**
     * Creates a candidate instance for registration and sends a request to register the instance with the registry server.
     */
    public void start() {
        var candidate = registryService.createCandidateFrom(serviceInfo);

        LOG.info("Registering service as serviceName [{}] on host [{}] at ports [{}]",
                candidate.getServiceName(), candidate.getHostName(), candidate.getPorts());

        registryService.register(candidate);
        LOG.info("Service registration sent");

        registryService.updateStatus(ServiceInstance.Status.UP);
    }

    /**
     * Triggers the de-registration of the service from the registry server.
     */
    public void stop() {
        LOG.info("Unregistering service");
        registryService.unregister();
        LOG.info("Service de-registration sent");
    }
}
