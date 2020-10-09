package org.kiwiproject.registry.consul.client;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.net.KiwiUrls.replaceDomainsIn;

import com.orbitz.consul.Consul;
import com.orbitz.consul.model.catalog.CatalogService;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.client.ServiceInstanceFilter;
import org.kiwiproject.registry.consul.config.ConsulConfig;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.model.ServicePaths;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

public class ConsulRegistryClient implements RegistryClient {

    private final Consul consul;
    private final ConsulConfig config;

    public ConsulRegistryClient(Consul consul, ConsulConfig config) {
        this.config = config;
        this.consul = consul;
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

        var port = Port.builder()
                .number(catalogService.getServicePort())
                .secure(Port.Security.fromScheme(scheme))
                .type(Port.PortType.APPLICATION)
                .build();

        var adminPort = Port.builder()
                .number(Integer.parseInt(metadata.get("adminPort")))
                .secure(Port.Security.fromScheme(scheme))
                .type(Port.PortType.ADMIN)
                .build();


        return ServiceInstance.builder()
                .instanceId(catalogService.getServiceId())
                .serviceName(catalogService.getServiceName())
                .hostName(adjustAddressIfNeeded(catalogService.getServiceAddress(), scheme))
                .ports(List.of(port, adminPort))
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
                .build();
    }

    private String adjustAddressIfNeeded(String hostname, String scheme) {
        if (isBlank(config.getDomainOverride())) {
            return hostname;
        }

        try {
            var url = new URL(replaceDomainsIn(scheme + "://" + hostname, config.getDomainOverride()));
            return url.getHost();
        } catch (MalformedURLException e) {
            return hostname;
        }
    }

}
