package org.kiwiproject.registry.eureka.client;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiThrowables.typeOfNullable;
import static org.kiwiproject.retry.KiwiRetryerPredicates.CONNECTION_ERROR;
import static org.kiwiproject.retry.KiwiRetryerPredicates.NO_ROUTE_TO_HOST;
import static org.kiwiproject.retry.KiwiRetryerPredicates.SOCKET_TIMEOUT;
import static org.kiwiproject.retry.KiwiRetryerPredicates.SSL_HANDSHAKE_ERROR;
import static org.kiwiproject.retry.KiwiRetryerPredicates.UNKNOWN_HOST;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.kiwiproject.jaxrs.KiwiGenericTypes;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.client.ServiceInstanceFilter;
import org.kiwiproject.registry.eureka.common.EurekaInstance;
import org.kiwiproject.registry.eureka.common.EurekaResponseParser;
import org.kiwiproject.registry.eureka.common.EurekaRestClient;
import org.kiwiproject.registry.eureka.common.EurekaUrlProvider;
import org.kiwiproject.registry.eureka.config.EurekaConfig;
import org.kiwiproject.registry.model.NativeRegistryData;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.retry.KiwiRetryer;
import org.kiwiproject.retry.WaitStrategies;
import org.kiwiproject.retry.WaitStrategy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Response;

/**
 * {@link RegistryClient} implementation for looking up services from Eureka registry server.
 */
@Slf4j
public class EurekaRegistryClient implements RegistryClient {

    /**
     * This number is multiplied by the number of Eureka URLs to determine the number of attempts that will be made
     * before giving up. For example, if there are two Eureka URLs, and the multiplier is 3, then a maximum of
     * (2 * 3) = 6 attempts will be made.
     */
    private static final int EUREKA_ATTEMPT_MULTIPLIER = 3;
    private static final int RETRY_MULTIPLIER = 100;
    private static final int RETRY_MAX_TIME = 30;
    private static final TimeUnit RETRY_MAX_TIME_UNIT = TimeUnit.SECONDS;

    private final EurekaRestClient client;
    private final EurekaUrlProvider urlProvider;
    private final KiwiRetryer<Response> clientRetryer;
    private final EurekaConfig config;

    public EurekaRegistryClient(EurekaConfig config, EurekaRestClient client) {
        this.client = client;
        this.urlProvider = new EurekaUrlProvider(config.getRegistryUrls());
        var maxAttempts = urlProvider.urlCount() * EUREKA_ATTEMPT_MULTIPLIER;
        this.clientRetryer = KiwiRetryer.<Response>builder()
                .retryerId(config.getRetryId())
                .processingLogLevel(config.getRetryProcessingLogLevel())
                .exceptionLogLevel(config.getRetryExceptionLogLevel())
                .exceptionPredicates(List.of(
                        CONNECTION_ERROR, NO_ROUTE_TO_HOST, SOCKET_TIMEOUT, SSL_HANDSHAKE_ERROR, UNKNOWN_HOST, temporaryServerSideStatusCodes()
                ))
                .maxAttempts(maxAttempts)
                .waitStrategy(getWaitStrategy())
                .build();
        this.config = config;
    }

    private static Predicate<Exception> temporaryServerSideStatusCodes() {
        return t -> {
            if (t instanceof ServerErrorException) {
                return checkIfDesiredStatusCodeValueIsFoundIn((ServerErrorException) t);
            }

            var rootCause = ExceptionUtils.getRootCause(t);

            if (rootCause instanceof ServerErrorException) {
                return checkIfDesiredStatusCodeValueIsFoundIn((ServerErrorException) rootCause);
            }

            LOG.warn("Will NOT retry after receiving {} error considered to be not temporary",
                    typeOfNullable(t).orElse("<unknown>"));
            LOG.debug("Error corresponding to above message:", t);
            return false;
        };
    }

    private static boolean checkIfDesiredStatusCodeValueIsFoundIn(ServerErrorException exception) {
        switch (Response.Status.fromStatusCode(exception.getResponse().getStatus())) {
            case BAD_GATEWAY:
            case SERVICE_UNAVAILABLE:
            case GATEWAY_TIMEOUT:
                return true;
            default:
                return false;
        }
    }

    /**
     * Allow tests to override to make the wait time much smaller, useful in tests that simulate multiple retry attempts
     */
    @VisibleForTesting
    WaitStrategy getWaitStrategy() {
        return WaitStrategies.exponentialWait(RETRY_MULTIPLIER, RETRY_MAX_TIME, RETRY_MAX_TIME_UNIT);
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

        var eurekaInstances = getRunningServiceInstancesFromEureka(query.getServiceName());

        var includeNativeData = config.isIncludeNativeData()
                ? NativeRegistryData.INCLUDE_NATIVE_DATA : NativeRegistryData.IGNORE_NATIVE_DATA;

        var serviceInstances = eurekaInstances.stream()
                .map(eurekaInstance -> eurekaInstance.toServiceInstance(includeNativeData))
                .collect(toList());

        return ServiceInstanceFilter.filterInstancesByVersion(serviceInstances, query);
    }

    private List<EurekaInstance> getRunningServiceInstancesFromEureka(String vipAddress) {
        var response = getRegisteredServicesFromEureka(vipAddress);

        if (isNull(response)) {
            return List.of();
        }

        return parseEurekaInstances(response);
    }

    private Response getRegisteredServicesFromEureka(String vipAddress) {
        return clientRetryer.call(() -> {
            var targetUrl = urlProvider.getCurrentEurekaUrl();

            LOG.debug("Attempting to lookup {} using {}", vipAddress, targetUrl);

            try {
                return client.findInstancesByVipAddress(targetUrl, vipAddress);
            } catch (Exception e) {
                urlProvider.getNextEurekaUrl();
                throw e;
            }
        });
    }

    @Override
    public List<ServiceInstance> retrieveAllRegisteredInstances() {
        var response = getAllRegisteredServicesFromEureka();

        if (isNull(response)) {
            return List.of();
        }

        var eurekaInstances = parseEurekaInstances(response);

        var includeNativeData = config.isIncludeNativeData()
                ? NativeRegistryData.INCLUDE_NATIVE_DATA : NativeRegistryData.IGNORE_NATIVE_DATA;

        return eurekaInstances.stream()
                .map(eurekaInstance -> eurekaInstance.toServiceInstance(includeNativeData))
                .collect(toList());
    }

    private static List<EurekaInstance> parseEurekaInstances(Response response) {
        var eurekaResponse = response.readEntity(KiwiGenericTypes.MAP_OF_STRING_TO_OBJECT_GENERIC_TYPE);
        return EurekaResponseParser.parseEurekaApplicationsResponse(eurekaResponse)
                .stream()
                .filter(instance -> ServiceInstance.Status.UP.name().equals(instance.getStatus()))
                .collect(toList());
    }

    private Response getAllRegisteredServicesFromEureka() {
        return clientRetryer.call(() -> {
            var targetUrl = urlProvider.getCurrentEurekaUrl();

            LOG.debug("Attempting to lookup all service instances using {}", targetUrl);

            try {
                return client.findAllInstances(targetUrl);
            } catch (Exception e) {
                urlProvider.getNextEurekaUrl();
                throw e;
            }
        });
    }
}
