package org.kiwiproject.registry.client;

import static java.util.stream.Collectors.toList;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.Versions;
import org.kiwiproject.registry.model.ServiceInstance;

import java.util.List;

@Slf4j
@UtilityClass
public class ServiceInstanceFilter {

    /**
     * Filter the given service instances using the {@code query}, by minimum and/or preferred versions if specified.
     *
     * @return an immutable list containing instances meeting the version criteria
     */
    public static List<ServiceInstance> filterInstancesByVersion(List<ServiceInstance> serviceInstances,
                                                                 RegistryClient.InstanceQuery query) {

        if (serviceInstances.isEmpty()) {
            LOG.trace("No running instances were found for service name {}", query.getServiceName());
            return List.of();
        }

        if (query.hasNoVersionPredicates()) {
            return List.copyOf(serviceInstances);
        }

        var instancesSatisfyingMinVersion = serviceInstances.stream()
                .filter(instance -> query.hasNoMinimumVersion() || versionIsAtLeast(instance, query.getMinimumVersion()))
                .collect(toList());

        if (instancesSatisfyingMinVersion.isEmpty()) {
            LOG.trace("No running instances for service name {} satisfy the minimum version {}",
                    query.getServiceName(), query.getMinimumVersion());
            return List.of();
        }

        var instancesSatisfyingPreferredVersion = instancesSatisfyingMinVersion.stream()
                .filter(instance -> query.hasNoPreferredVersion() || versionIsExactly(instance, query.getPreferredVersion()))
                .collect(toList());

        if (instancesSatisfyingPreferredVersion.isEmpty()) {
            LOG.trace("No running instances for service name {} match preferred version {}; finding latest instead",
                    query.getServiceName(), query.getPreferredVersion());
            return List.copyOf(findInstancesWithLatestVersion(serviceInstances));
        }

        return List.copyOf(instancesSatisfyingPreferredVersion);
    }

    /**
     * Is the version of the {@link ServiceInstance} the same or higher than {@code version}?
     *
     * @implNote Compares versions using Kiwi's {@link Versions#isHigherOrSameVersion(String, String)}
     */
    public static boolean versionIsAtLeast(ServiceInstance instance, String version) {
        validateVersions(instance, version);

        return Versions.isHigherOrSameVersion(instance.getVersion(), version);
    }

    /**
     * Is the version of the {@link ServiceInstance} the same as {@code version}?
     *
     * @implNote Compares versions using Kiwi's {@link Versions#isSameVersion(String, String)}
     */
    public static boolean versionIsExactly(ServiceInstance instance, String version) {
        validateVersions(instance, version);

        return Versions.isSameVersion(instance.getVersion(), version);
    }

    private static void validateVersions(ServiceInstance instance, String version) {
        checkArgumentNotBlank(version, "version to compare cannot be blank");
        checkArgumentNotNull(instance.getVersion(), "instance version cannot be null");
    }

    /**
     * Finds the latest version in the given list of service instances, then returns a new list containing only
     * instances having that latest version.
     *
     * @implNote Compares versions using Kiwi's {@link Versions#versionCompare(String, String)}
     */
    public static List<ServiceInstance> findInstancesWithLatestVersion(List<ServiceInstance> serviceInstances) {
        var maxVersion = serviceInstances.stream()
                .map(ServiceInstance::getVersion)
                .max(Versions::versionCompare)
                .orElseThrow(IllegalStateException::new);

        return serviceInstances.stream()
                .filter(instance -> versionIsExactly(instance, maxVersion))
                .collect(toList());
    }
}
