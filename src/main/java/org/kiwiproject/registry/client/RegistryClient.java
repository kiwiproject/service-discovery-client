package org.kiwiproject.registry.client;

import static org.apache.commons.lang3.StringUtils.isBlank;

import lombok.Builder;
import lombok.Getter;
import org.kiwiproject.registry.model.ServiceInstance;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Base of all registry client implementations in order to find running services
 */
public interface RegistryClient {

    /**
     * Encapsulates search parameters for finding service instances
     * <p>
     * Note: serviceName is required
     */
    @Builder
    @Getter
    class InstanceQuery {

        private final String serviceName;
        private final String minimumVersion;
        private final String preferredVersion;

        public boolean hasNoVersionPredicates() {
            return hasNoMinimumVersion() && hasNoPreferredVersion();
        }

        public boolean hasNoMinimumVersion() {
            return isBlank(minimumVersion);
        }

        public boolean hasNoPreferredVersion() {
            return isBlank(preferredVersion);
        }
    }

    /**
     * Attempts to find a service instance with the given service name.
     * <p>
     * If there are more than one instance, the implementation should decide the order in which the service is returned
     * (e.g. round robin, random, LIFO, FIFO, etc)
     *
     * @param serviceName The name of the service that is being requested
     * @return an {@link Optional} containing the found service or {@code Optional.empty()}
     */
    default Optional<ServiceInstance> findServiceInstanceBy(String serviceName) {
        return findServiceInstanceBy(InstanceQuery.builder().serviceName(serviceName).build());
    }

    /**
     * Attempts to find a service instance with the given service name and the given instance id.
     *
     * @param serviceName The name of the service that is being requested
     * @param instanceId The id of the instance that is wanted
     * @return an {@link Optional} containing the found service or {@code Optional.empty()}
     */
    Optional<ServiceInstance> findServiceInstanceBy(String serviceName, String instanceId);

    /**
     * Attempts to find a service instance from the given {@link InstanceQuery}.
     * <p>
     * If there are more than one instance, the implementation should decide the order in which the service is returned
     * (e.g. round robin, random, LIFO, FIFO, etc)
     *
     * @param query a {@link InstanceQuery} containing the search parameters to find the instance
     * @return an {@link Optional} containing the found service or {@code Optional.empty()}
     */
    default Optional<ServiceInstance> findServiceInstanceBy(InstanceQuery query) {
        var instances = findAllServiceInstancesBy(query);

        if (instances.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(selectRandom(instances));
    }

    /**
     * Attempts to find all service instances with the given service name.
     *
     * @param serviceName The name of the service that is being requested
     * @return a {@link List} containing the found services
     */
    default List<ServiceInstance> findAllServiceInstancesBy(String serviceName) {
        return findAllServiceInstancesBy(InstanceQuery.builder().serviceName(serviceName).build());
    }

    /**
     * Attempts to find all service instances from the given {@link InstanceQuery}.
     *
     * @param query a {@link InstanceQuery} containing the search parameters to find the instance
     * @return a {@link List} containing the found services
     */
    List<ServiceInstance> findAllServiceInstancesBy(InstanceQuery query);

    private static <T> T selectRandom(List<T> items) {
        var index = selectRandomIndex(items);
        return items.get(index);
    }

    private static <T> int selectRandomIndex(List<T> items) {
        return ThreadLocalRandom.current().nextInt(items.size());
    }
}
