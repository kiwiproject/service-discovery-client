package org.kiwiproject.registry.client;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotEmpty;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

import com.google.common.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.Getter;
import org.kiwiproject.collect.KiwiLists;
import org.kiwiproject.registry.model.ServiceInstance;

import java.util.List;
import java.util.Optional;

/**
 * A {@link RegistryClient} that finds services in more than one registry.
 */
public class MultiRegistryClient implements RegistryClient {

    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    private final List<RegistryClient> registryClients;

    /**
     * Construct a new instance with the specfied {@link RegistryClient}s to use when performing service
     * lookups. The order in the provided list is the order in which the service lookups will occur.
     *
     * @param registryClients a list of {@link RegistryClient} instances to use; must have at least one RegistryClient
     */
    public MultiRegistryClient(List<RegistryClient> registryClients) {
        checkArgumentNotNull(registryClients, "registryClients must not be null");
        checkArgumentNotEmpty(registryClients, "registryClients must not be empty");
        this.registryClients = List.copyOf(registryClients);
    }

    /**
     * Factory method to create a new instance from the given {@link RegistryClient}s.
     *
     * @param registryClients the {@link RegistryClient} instances to use; must have at least one RegistryClient
     * @return a new MultiRegistryClient instance
     */
    public static MultiRegistryClient of(RegistryClient... registryClients) {
        checkArgumentNotNull(registryClients, "registryClients varargs must not be null");  // should not happen unless a caller is dumb
        checkArgument(registryClients.length > 0, "at least one RegistryClient must be provided");
        return new MultiRegistryClient(List.of(registryClients));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the first {@link ServiceInstance} it finds, checking each {@link RegistryClient} in order.
     */
    @Override
    public Optional<ServiceInstance> findServiceInstanceBy(String serviceName, String instanceId) {
        return registryClients.stream()
                .map(registryClient -> registryClient.findServiceInstanceBy(serviceName, instanceId))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns all services that match the query from all {@link RegistryClient}s that return any results, checking
     * each {@link RegistryClient} in order.
     */
    @Override
    public List<ServiceInstance> findAllServiceInstancesBy(InstanceQuery query) {
        return registryClients.stream()
                .map(registryClient -> registryClient.findAllServiceInstancesBy(query))
                .filter(KiwiLists::isNotNullOrEmpty)
                .flatMap(List::stream)
                .collect(toUnmodifiableList());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns all registered services from all {@link RegistryClient}s that return any results, checking each
     * {@link RegistryClient} in order.
     */
    @Override
    public List<ServiceInstance> retrieveAllRegisteredInstances() {
        return registryClients.stream()
                .map(RegistryClient::retrieveAllRegisteredInstances)
                .filter(KiwiLists::isNotNullOrEmpty)
                .flatMap(List::stream)
                .collect(toUnmodifiableList());
    }
}
