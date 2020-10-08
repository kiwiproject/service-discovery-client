package org.kiwiproject.registry.consul.server;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.kiwiproject.base.KiwiStrings.format;
import static org.kiwiproject.net.KiwiUrls.replaceDomainsIn;
import static org.kiwiproject.registry.util.Paths.urlForPath;
import static org.kiwiproject.registry.util.Ports.determineScheme;
import static org.kiwiproject.registry.util.Ports.findFirstPortPreferSecure;

import com.orbitz.consul.Consul;
import com.orbitz.consul.model.agent.ImmutableRegCheck;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.base.UUIDs;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.consul.config.ConsulRegistrationConfig;
import org.kiwiproject.registry.exception.RegistrationException;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.server.RegistryService;
import org.kiwiproject.retry.SimpleRetryer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link RegistryService} implementation for Consul discovery servers
 *
 * NOTE: This class is intended to manage the registration and unregistration of a service with Consul.  If your service
 * is using a framework that has Consul integration, you might not want to use this class as it may attempt to register
 * more than once (once from here and once from the framework integration)
 */
@Slf4j
public class ConsulRegistryService implements RegistryService {

    /**
     * Maximum number of attempts we will make trying to register with Eureka (via POST)
     */
    private static final int MAX_REGISTRATION_ATTEMPTS = 60;

    /**
     * Value for delay to wait if a call to Eureka fails.
     */
    private static final long RETRY_DELAY = 1;

    /**
     * Unit for delay to wait if a call to Eureka fails.
     */
    private static final TimeUnit RETRY_DELAY_UNIT = TimeUnit.SECONDS;

    private final Consul consul;
    private final ConsulRegistrationConfig config;
    private final AtomicReference<ServiceInstance> registeredService;
    private final SimpleRetryer registerRetryer;

    public ConsulRegistryService(Consul consul, ConsulRegistrationConfig config, KiwiEnvironment environment) {
        this.consul = consul;
        this.config = config;
        this.registeredService = new AtomicReference<>();

        this.registerRetryer = SimpleRetryer.builder()
                .environment(environment)
                .maxAttempts(MAX_REGISTRATION_ATTEMPTS)
                .retryDelayTime(RETRY_DELAY)
                .retryDelayUnit(RETRY_DELAY_UNIT)
                .build();
    }

    @Override
    public ServiceInstance createCandidateFrom(ServiceInfo serviceInfo) {
        return ServiceInstance.fromServiceInfo(serviceInfo)
                .withStatus(ServiceInstance.Status.UP);
    }

    @Override
    public ServiceInstance register(ServiceInstance serviceToRegister) {
        checkState(!isRegistered(), "Cannot register. Already managing a registered instance: %s",
                registeredService.get().getInstanceId());

        var registration = fromServiceInstance(serviceToRegister);
        consul.agentClient().register(registration);

        var service = registerRetryer.tryGetObject(() -> {
            consul.agentClient().register(registration);
            return serviceToRegister.withInstanceId(registration.getId());
        });

        if (service.isEmpty()) {
            LOG.error("Registration failed for service {} with id {}. See logs above", serviceToRegister.getServiceName(), registration.getId());
            var errMsg = format("Unable to register service %s, id %s with Consul after %s attempts",
                    serviceToRegister.getServiceName(), registration.getId(), MAX_REGISTRATION_ATTEMPTS);
            throw new RegistrationException(errMsg);
        }

        registeredService.set(service.get());
        return registeredService.get().toBuilder().build();
    }

    @Override
    public ServiceInstance updateStatus(ServiceInstance.Status newStatus) {
        LOG.info("Ignoring status update as Consul handles it's own status through it's health check process");

        if (isRegistered()) {
            return null;
        }

        return registeredService.get().toBuilder().build();
    }

    @Override
    public void unregister() {
        checkState(isRegistered(), "Cannot unregister since registration was never called");

        LOG.info("Unregistering service {} with id {}", registeredService.get().getServiceName(), registeredService.get().getInstanceId());
        consul.agentClient().deregister(registeredService.get().getInstanceId());
    }

    private boolean isRegistered() {
        return nonNull(registeredService.get());
    }

    private Registration fromServiceInstance(ServiceInstance serviceInstance) {

        var check = ImmutableRegCheck.builder()
                .http(urlForPath(serviceInstance.getHostName(), serviceInstance.getPorts(), Port.PortType.ADMIN,
                        serviceInstance.getPaths().getStatusPath()))
                .interval(String.format("%ds", config.getCheckIntervalInSeconds()))
                .deregisterCriticalServiceAfter(
                        String.format("%dm", config.getDeregisterIntervalInMinutes()))
                .build();

        var address = isBlank(config.getDomainOverride()) ? serviceInstance.getHostName() :
                replaceDomainsIn(serviceInstance.getHostName(), config.getDomainOverride());

        return ImmutableRegistration.builder()
                .port(findFirstPortPreferSecure(serviceInstance.getPorts(), Port.PortType.APPLICATION).getNumber())
                .check(check)
                .id(UUIDs.randomUUIDString())
                .name(serviceInstance.getServiceName())
                .address(address)
                .tags(List.of("service-type:default"))
                .meta(Map.of(
                        "version", serviceInstance.getVersion(),
                        "commitRef", serviceInstance.getCommitRef(),
                        "description", serviceInstance.getDescription(),
                        "homePagePath", serviceInstance.getPaths().getHomePagePath(),
                        "healthCheckPath", serviceInstance.getPaths().getHealthCheckPath(),
                        "statusCheckPath", serviceInstance.getPaths().getStatusPath(),
                        "scheme", determineScheme(serviceInstance.getPorts(), Port.PortType.APPLICATION)
                ))
                .build();
    }

}
