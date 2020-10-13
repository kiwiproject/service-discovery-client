package org.kiwiproject.registry.consul.client;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.collect.KiwiLists.isNotNullOrEmpty;
import static org.kiwiproject.net.KiwiUrls.replaceDomainsIn;

import com.orbitz.consul.Consul;
import com.orbitz.consul.model.catalog.CatalogService;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.client.ServiceInstanceFilter;
import org.kiwiproject.registry.consul.config.ConsulConfig;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.model.ServicePaths;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ConsulRegistryClient implements RegistryClient {

    private static final String ADMIN_PORT_FIELD = "adminPort";
    private static final List<String> METADATA_EXCLUDED_KEYS = List.of("commitRef", "description", "version", "homePagePath", "healthCheckPath", "statusPath",
            "scheme", ADMIN_PORT_FIELD, "ipAddress");

    private static final Set<String> TAGS_EXCLUDED = Set.of("service-type:default");

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
        var convertedServices = services.stream().map(this::fromCatalogService).collect(toList());

        return ServiceInstanceFilter.filterInstancesByVersion(convertedServices, query);
    }

    private ServiceInstance fromCatalogService(CatalogService catalogService) {
        var metadata = catalogService.getServiceMeta();
        var scheme = metadata.get("scheme");

        var ports = new ArrayList<Port>();
        var port = Port.builder()
                .number(catalogService.getServicePort())
                .secure(Port.Security.fromScheme(scheme))
                .type(Port.PortType.APPLICATION)
                .build();
        ports.add(port);

        if (isNotBlank(metadata.get(ADMIN_PORT_FIELD))) {
            var adminPort = Port.builder()
                    .number(Integer.parseInt(metadata.get(ADMIN_PORT_FIELD)))
                    .secure(Port.Security.fromScheme(scheme))
                    .type(Port.PortType.ADMIN)
                    .build();
            ports.add(adminPort);
        }

        var serviceMetadata = filterMetadata(metadata);
        addTagsToMetadata(serviceMetadata, catalogService.getServiceTags());

        return ServiceInstance.builder()
                .instanceId(catalogService.getServiceId())
                .serviceName(catalogService.getServiceName())
                .hostName(adjustAddressIfNeeded(catalogService.getServiceAddress(), scheme))
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
                .build();
    }

    private Map<String, String> filterMetadata(Map<String, String> metadata) {
        return metadata.entrySet().stream()
                .filter(entry -> !METADATA_EXCLUDED_KEYS.contains(entry.getKey()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void addTagsToMetadata(Map<String, String> metadata, List<String> tags) {
        var filteredTags = tags.stream()
                .filter(tag -> !TAGS_EXCLUDED.contains(tag))
                .collect(toList());

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

    private String adjustAddressIfNeeded(String hostname, String scheme) {
        if (isBlank(config.getDomainOverride())) {
            return hostname;
        }

        try {
            var url = new URL(replaceDomainsIn(scheme + "://" + hostname, config.getDomainOverride()));
            return url.getHost();
        } catch (Exception e) {
            return hostname;
        }
    }

}
