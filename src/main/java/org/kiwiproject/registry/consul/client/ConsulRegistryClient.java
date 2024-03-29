package org.kiwiproject.registry.consul.client;

import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.collect.KiwiLists.isNotNullOrEmpty;

import com.google.common.annotations.VisibleForTesting;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.model.catalog.CatalogService;
import org.kiwiproject.json.JsonHelper;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.client.ServiceInstanceFilter;
import org.kiwiproject.registry.consul.config.ConsulConfig;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.model.ServicePaths;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ConsulRegistryClient implements RegistryClient {

    private static final String ADMIN_PORT_FIELD = "adminPort";
    private static final String SERVICE_UP_TIMESTAMP_FIELD = "serviceUpTimestamp";
    private static final List<String> METADATA_EXCLUDED_KEYS = List.of("commitRef", "description", "version", "homePagePath", "healthCheckPath", "statusPath",
            "scheme", ADMIN_PORT_FIELD, "ipAddress");

    private static final Set<String> TAGS_EXCLUDED = Set.of("service-type:default");
    private static final JsonHelper JSON_HELPER = new JsonHelper();

    private final Consul consul;
    private final ConsulConfig config;

    public ConsulRegistryClient(Consul consul, ConsulConfig config) {
        this.config = requireNotNull(config);
        this.consul = requireNotNull(consul);
    }

    @Override
    public Optional<ServiceInstance> findServiceInstanceBy(String serviceName, String instanceId) {
        checkArgumentNotBlank(instanceId, "The instance ID cannot be blank");

        var instances = findAllServiceInstancesBy(serviceName);

        return instances.stream()
                .filter(instance -> instance.getInstanceId().equals(instanceId))
                .findFirst();
    }

    @Override
    public List<ServiceInstance> findAllServiceInstancesBy(InstanceQuery query) {
        checkArgumentNotNull(query, "The query cannot be null");
        checkArgumentNotBlank(query.getServiceName(), "The service name cannot be blank");

        var services = consul.catalogClient().getService(query.getServiceName()).getResponse();
        var convertedServices = services.stream().map(this::fromCatalogService).toList();

        return ServiceInstanceFilter.filterInstancesByVersion(convertedServices, query);
    }

    private ServiceInstance fromCatalogService(CatalogService catalogService) {
        var metadata = catalogService.getServiceMeta();
        var scheme = metadata.get("scheme");

        var ports = new ArrayList<Port>();
        var port = Port.of(catalogService.getServicePort(), PortType.APPLICATION, Security.fromScheme(scheme));
        ports.add(port);

        if (isNotBlank(metadata.get(ADMIN_PORT_FIELD))) {
            var adminPortNumber = getAdminPortNumberOrThrow(metadata);
            var adminPort = Port.of(adminPortNumber, PortType.ADMIN, Security.fromScheme(scheme));
            ports.add(adminPort);
        }

        var serviceMetadata = filterMetadata(metadata);
        addTagsToMetadata(serviceMetadata, catalogService.getServiceTags());
        serviceMetadata.put("registryType", "CONSUL");

        var upSince = metadata.containsKey(SERVICE_UP_TIMESTAMP_FIELD)
                ? Instant.ofEpochMilli(getServiceUpTimestampOrThrow(metadata)) : Instant.EPOCH;

        var instance = ServiceInstance.builder()
                .instanceId(catalogService.getServiceId())
                .serviceName(catalogService.getServiceName())
                .hostName(catalogService.getServiceAddress())
                .ports(ports)
                .paths(ServicePaths.builder()
                        .homePagePath(metadata.get("homePagePath"))
                        .statusPath(metadata.get("statusPath"))
                        .healthCheckPath(metadata.get("healthCheckPath"))
                        .build())
                .commitRef(metadata.get("commitRef"))
                .description(metadata.get("description"))
                .version(metadata.get("version"))
                .status(ServiceInstance.Status.UP)
                .ip(metadata.get("ipAddress"))
                .metadata(serviceMetadata)
                .upSince(upSince)
                .build();

        if (config.isIncludeNativeData()) {
            return instance.withNativeRegistryData(JSON_HELPER.convertToMap(catalogService));
        }

        return instance.withNativeRegistryData(Map.of());
    }

    @VisibleForTesting
    static int getAdminPortNumberOrThrow(Map<String, String> metadata) {
        var adminPortString = metadata.get(ADMIN_PORT_FIELD);
        try {
            return Integer.parseInt(adminPortString);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(ADMIN_PORT_FIELD + " is not a number", e);
        }
    }

    @VisibleForTesting
    static long getServiceUpTimestampOrThrow(Map<String, String> metadata) {
        var serviceUpTimestampString = metadata.get(SERVICE_UP_TIMESTAMP_FIELD);
        try {
            return Long.parseLong(serviceUpTimestampString);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(SERVICE_UP_TIMESTAMP_FIELD + " is not a number", e);
        }
    }

    private Map<String, String> filterMetadata(Map<String, String> metadata) {
        return metadata.entrySet().stream()
                .filter(entry -> !METADATA_EXCLUDED_KEYS.contains(entry.getKey()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void addTagsToMetadata(Map<String, String> metadata, List<String> tags) {
        var filteredTags = tags.stream()
                .filter(tag -> !TAGS_EXCLUDED.contains(tag))
                .toList();

        if (isNotNullOrEmpty(filteredTags)) {
            filteredTags.forEach(tag -> {
                if (tag.contains(":")) {
                    var tagSplit = tag.split(":");
                    metadata.put(tagSplit[0], tagSplit[1]);
                } else {
                    metadata.put(tag, tag);
                }
            });
        }
    }

    /**
     * Returns all registered services in Consul.
     *
     * @return a {@link List} containing all registered service instances
     * @implNote This will return ALL services in Consul (including Consul itself) and attempt to map it into a
     * {@link ServiceInstance} object
     */
    @Override
    public List<ServiceInstance> retrieveAllRegisteredInstances() {
        return consul.catalogClient().getServices().getResponse().keySet().stream()
                .map(this::findAllServiceInstancesBy)
                .flatMap(List::stream)
                .toList();
    }
}
