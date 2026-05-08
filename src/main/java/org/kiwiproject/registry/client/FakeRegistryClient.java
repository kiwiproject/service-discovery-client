package org.kiwiproject.registry.client;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

import org.kiwiproject.registry.model.ServiceInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A fake {@link RegistryClient} implementation intended for use in tests. It maintains an in-memory
 * mapping of service names to {@link ServiceInstance} lists, allowing tests to pre-register instances
 * and verify lookup behavior without connecting to a real service registry.
 * <p>
 * Example usage:
 * <pre>
 * var fakeClient = new FakeRegistryClient();
 * fakeClient.addServiceInstance(ServiceInstance.builder()
 *         .serviceName("order-service")
 *         .instanceId("order-1")
 *         .hostName("localhost")
 *         .version("1.0.0")
 *         .build());
 *
 * Optional&lt;ServiceInstance&gt; found = fakeClient.findServiceInstanceBy("order-service");
 * </pre>
 */
public class FakeRegistryClient implements RegistryClient {

    private final Map<String, List<ServiceInstance>> serviceInstances;

    /**
     * Creates an empty {@code FakeRegistryClient} with no pre-registered instances.
     */
    public FakeRegistryClient() {
        this.serviceInstances = new HashMap<>();
    }

    /**
     * Creates a {@code FakeRegistryClient} pre-populated with the given instances.
     *
     * @param instances the initial list of service instances to register; must not be null
     */
    public FakeRegistryClient(List<ServiceInstance> instances) {
        this();
        checkArgumentNotNull(instances, "instances must not be null");
        instances.forEach(this::addServiceInstance);
    }

    /**
     * Registers a {@link ServiceInstance} with this client, keyed by its service name.
     *
     * @param instance the instance to add; must not be null
     */
    public void addServiceInstance(ServiceInstance instance) {
        checkArgumentNotNull(instance, "instance must not be null");
        serviceInstances
                .computeIfAbsent(instance.getServiceName(), k -> new ArrayList<>())
                .add(instance);
    }

    /**
     * Registers multiple {@link ServiceInstance} objects with this client.
     *
     * @param instances the instances to add; must not be null
     */
    public void addServiceInstances(List<ServiceInstance> instances) {
        checkArgumentNotNull(instances, "instances must not be null");
        instances.forEach(this::addServiceInstance);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Finds the instance matching both service name and instance ID, or returns empty if not found.
     */
    @Override
    public Optional<ServiceInstance> findServiceInstanceBy(String serviceName, String instanceId) {
        return findAllServiceInstancesBy(serviceName).stream()
                .filter(instance -> instanceId.equals(instance.getInstanceId()))
                .findFirst();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns all instances matching the query's service name, filtered by version predicates if present.
     */
    @Override
    public List<ServiceInstance> findAllServiceInstancesBy(InstanceQuery query) {
        var instances = serviceInstances.getOrDefault(query.getServiceName(), List.of());
        return ServiceInstanceFilter.filterInstancesByVersion(instances, query);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns all registered instances across all service names.
     */
    @Override
    public List<ServiceInstance> retrieveAllRegisteredInstances() {
        return serviceInstances.values().stream()
                .flatMap(List::stream)
                .toList();
    }
}
