package org.kiwiproject.registry.consul.server;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.kiwiproject.base.KiwiStrings.format;
import static org.kiwiproject.net.KiwiUrls.replaceDomainsIn;
import static org.kiwiproject.registry.util.Paths.urlForPath;
import static org.kiwiproject.registry.util.Ports.determineScheme;
import static org.kiwiproject.registry.util.Ports.findFirstPortPreferSecure;

import com.google.common.annotations.VisibleForTesting;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.agent.ImmutableRegCheck;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.base.Optionals;
import org.kiwiproject.base.UUIDs;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.consul.config.ConsulRegistrationConfig;
import org.kiwiproject.registry.exception.RegistrationException;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.server.RegistryService;
import org.kiwiproject.registry.util.Paths;
import org.kiwiproject.retry.SimpleRetryer;

import java.net.MalformedURLException;
import java.net.URL;
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
    @VisibleForTesting
    static final int MAX_REGISTRATION_ATTEMPTS = 60;

    /**
     * Value for delay to wait if a call to Eureka fails.
     */
    private static final long RETRY_DELAY = 1;

    /**
     * Unit for delay to wait if a call to Eureka fails.
     */
    private static final TimeUnit RETRY_DELAY_UNIT = TimeUnit.SECONDS;

    /**
     * Value for delay when un-registering if a call to Eureka fails.
     */
    private static final long UNREGISTER_RETRY_DELAY = 3;

    /**
     * Time unit for delay when un-registering if a call to Eureka fails.
     */
    private static final TimeUnit UNREGISTER_RETRY_DELAY_UNIT = TimeUnit.SECONDS;

    /**
     * Maximum number of attempts we will make to un-register from Eureka (via DELETE request).
     */
    private static final int MAX_UNREGISTER_ATTEMPTS = 5;

    private final Consul consul;
    private final ConsulRegistrationConfig config;
    private final SimpleRetryer registerRetryer;
    private final SimpleRetryer unregisterRetryer;

    @VisibleForTesting
    final AtomicReference<ServiceInstance> registeredService;

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

        this.unregisterRetryer = SimpleRetryer.builder()
                .environment(environment)
                .maxAttempts(MAX_UNREGISTER_ATTEMPTS)
                .retryDelayTime(UNREGISTER_RETRY_DELAY)
                .retryDelayUnit(UNREGISTER_RETRY_DELAY_UNIT)
                .build();
    }

    @Override
    public ServiceInstance createCandidateFrom(ServiceInfo serviceInfo) {
        return ServiceInstance.fromServiceInfo(serviceInfo)
                .withStatus(ServiceInstance.Status.UP);
    }

    @Override
    public ServiceInstance register(ServiceInstance serviceToRegister) {
        checkState(!isRegistered(), "Cannot register. Already managing a registered instance");

        var registration = fromServiceInstance(serviceToRegister);

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
            return registeredService.get().toBuilder().build();
        }

        return null;
    }

    @Override
    public void unregister() {
        checkState(isRegistered(), "Cannot unregister since registration was never called");

        var serviceName = registeredService.get().getServiceName();
        var instanceId = registeredService.get().getInstanceId();

        LOG.info("Unregistering service {} with id {}", serviceName, instanceId);

        var result = unregisterRetryer.tryGetObject(() -> {
            consul.agentClient().deregister(registeredService.get().getInstanceId());
            return instanceId;
        });

        Optionals.ifPresentOrElseThrow(result,
                id -> {
                    LOG.info("Service with name {} and id {} has been unregistered successfully", serviceName, instanceId);
                    registeredService.set(null);
                },
                () -> {
                    var msg = format("Error un-registering service {}, id {}", serviceName, instanceId);
                    LOG.error(msg);
                    return new RegistrationException(msg);
                });
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

        var address = adjustAddressIfNeeded(serviceInstance);

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

    private String adjustAddressIfNeeded(ServiceInstance instance) {
        if (isBlank(config.getDomainOverride())) {
            return instance.getHostName();
        }

        var urlString = Paths.urlForPath(instance.getHostName(), instance.getPorts(), Port.PortType.APPLICATION, instance.getPaths().getHomePagePath());
        try {
            var url = new URL(replaceDomainsIn(urlString, config.getDomainOverride()));
            return url.getHost();
        } catch (MalformedURLException e) {
            return instance.getHostName();
        }
    }

}
