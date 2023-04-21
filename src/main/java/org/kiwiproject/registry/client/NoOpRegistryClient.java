package org.kiwiproject.registry.client;

import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.registry.model.ServiceInstance;

import java.util.List;
import java.util.Optional;

/**
 * A "no-op" implementation of {@link RegistryClient}.
 */
@Slf4j
public class NoOpRegistryClient implements RegistryClient {

    /**
     * Always returns an empty Optional.
     *
     * @param serviceName ignored
     * @param instanceId ignored
     * @return empty Optional
     */
    @Override
    public Optional<ServiceInstance> findServiceInstanceBy(String serviceName, String instanceId) {
        LOG.warn("NoOpRegistryClient#findServiceInstanceBy always returns empty Optional<ServiceInstance>");
        return Optional.empty();
    }

    /**
     * Always returns an empty, unmodifiable list.
     *
     * @param query ignored
     * @return empty unmodifiable list
     */
    @Override
    public List<ServiceInstance> findAllServiceInstancesBy(InstanceQuery query) {
        LOG.warn("NoOpRegistryClient#findAllServiceInstancesBy always returns empty List<ServiceInstance>");
        return List.of();
    }

    /**
     * Always returns an empty, unmodifiable list.
     *
     * @return empty unmodifiable list
     */
    @Override
    public List<ServiceInstance> retrieveAllRegisteredInstances() {
        LOG.warn("NoOpRegistryClient#retrieveAllRegisteredInstances always returns empty List<ServiceInstance>");
        return List.of();
    }
}
