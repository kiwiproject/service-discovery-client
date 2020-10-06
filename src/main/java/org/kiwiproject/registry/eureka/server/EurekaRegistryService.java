package org.kiwiproject.registry.eureka.server;

import static com.google.common.base.Preconditions.checkState;
import static java.time.ZoneOffset.UTC;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.base.KiwiStrings.format;
import static org.kiwiproject.collect.KiwiLists.isNotNullOrEmpty;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.base.Optionals;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.eureka.common.EurekaInstance;
import org.kiwiproject.registry.eureka.common.EurekaRestClient;
import org.kiwiproject.registry.eureka.common.EurekaUrlProvider;
import org.kiwiproject.registry.eureka.config.EurekaRegistrationConfig;
import org.kiwiproject.registry.exception.RegistrationException;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.server.RegistryService;
import org.kiwiproject.retry.SimpleRetryer;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public class EurekaRegistryService implements RegistryService {

    /**
     * @implNote Need to specify the override zone via {@link DateTimeFormatter#withZone(ZoneId)}, otherwise when trying
     * to format a {@link Date} the formatter will fail with the error:
     * {@code "java.time.temporal.UnsupportedTemporalTypeException: Unsupported field: YearOfEra"}
     * <p>
     * This is because {@link DateTimeFormatter#format(TemporalAccessor)} accepts a {@link java.time.temporal.TemporalAccessor}
     * of which {@link Date} is definitely not an instance. You therefore need to convert the Date to an Instant and
     * then call format. However, a time zone is required to format an Instant, thus this is an easy way to do it
     * globally for any Date instance that is converted to an Instant.
     */
    @VisibleForTesting
    static final DateTimeFormatter APP_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss.SSS").withZone(UTC);

    /**
     * Funny story ... this class doesn't actually exist. It's for "backwards compatibility.
     * <p>
     * See com.netflix.discovery.converters.jackson.DataCenterTypeInfoResolver for details.
     * (NOTE: javadoc annotation not used to avoid unnecessary errors.)
     */
    private static final String DEFAULT_DATA_CENTER_INFO_CLASS = "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo";

    private static final String DEFAULT_DATA_CENTER_NAME = "MyOwn";

    /**
     * Key for lease info representing lease duration in seconds.
     */
    private static final String LEASE_DURATION_IN_SECONDS = "durationInSecs";

    /**
     * Key for lease info representing renewal interval in seconds.
     */
    private static final String LEASE_RENEWAL_INTERVAL_IN_SECONDS = "renewalIntervalInSecs";

    /**
     * Maximum number of attempts we will make to await confirmation that we're registered with Eureka.
     */
    public static final int MAX_AWAIT_REGISTRATION_CONFIRMATION_TRIES = 10;

    /**
     * Maximum number of attempts we will make trying to register with Eureka (via POST)
     */
    public static final int MAX_REGISTRATION_ATTEMPTS = 60;

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

    /**
     * Maximum number of attempts we will make to update status in Eureka (via PUT request).
     */
    private static final int MAX_UPDATE_STATUS_ATTEMPTS = 5;

    private final EurekaRegistrationConfig config;
    private final EurekaRestClient client;
    private final SimpleRetryer registerRetryer;
    private final SimpleRetryer awaitRetryer;
    private final SimpleRetryer updateStatusRetryer;
    private final SimpleRetryer unregisterRetryer;
    private final KiwiEnvironment environment;
    private final EurekaUrlProvider urlProvider;

    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    final AtomicReference<EurekaInstance> registeredInstance;

    @VisibleForTesting
    final AtomicReference<ScheduledExecutorService> heartbeatExecutor;

    public EurekaRegistryService(EurekaRegistrationConfig config, EurekaRestClient client, KiwiEnvironment environment) {
        this.config = config;
        this.client = client;
        this.environment = environment;
        this.urlProvider = new EurekaUrlProvider(config.getRegistryUrls());
        this.registeredInstance = new AtomicReference<>();
        this.heartbeatExecutor = new AtomicReference<>();

        this.registerRetryer = SimpleRetryer.builder()
                .environment(environment)
                .maxAttempts(MAX_REGISTRATION_ATTEMPTS)
                .retryDelayTime(RETRY_DELAY)
                .retryDelayUnit(RETRY_DELAY_UNIT)
                .build();

        this.awaitRetryer = SimpleRetryer.builder()
                .environment(environment)
                .maxAttempts(MAX_AWAIT_REGISTRATION_CONFIRMATION_TRIES)
                .retryDelayTime(RETRY_DELAY)
                .retryDelayUnit(RETRY_DELAY_UNIT)
                .build();

        this.updateStatusRetryer = SimpleRetryer.builder()
                .environment(environment)
                .maxAttempts(MAX_UPDATE_STATUS_ATTEMPTS)
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
                .withStatus(ServiceInstance.Status.STARTING);
    }

    @Override
    public ServiceInstance register(ServiceInstance serviceToRegister) {
        checkState(isNotRegistered(), "Cannot register. Already managing a registered instance: %s", registeredInstance.get());

        // NOTE: We are calling `toUpperCase()` because the Eureka server will do it on the server side anyways, so this keeps it consistent here.
        var appId = f("{}-{}", serviceToRegister.getServiceName(), APP_TIMESTAMP_FORMATTER.format(environment.currentInstant()))
                .toUpperCase(Locale.getDefault());

        registerWithEureka(appId, serviceToRegister);

        var registeredInstanceFromEureka = waitForInstanceToBeRegistered(appId, serviceToRegister.getHostName());

        // I'm pretty sure this can never happen since waitForInstanceToBeRegistered will throw an exception after the retries run out
        if (isNull(registeredInstanceFromEureka)) {
            return null;
        }

        registeredInstance.set(registeredInstanceFromEureka);
        LOG.info("Successful registration of app {}, instance {} with vip address {}",
                registeredInstanceFromEureka.getApp(), registeredInstanceFromEureka.getInstanceId(), registeredInstanceFromEureka.getVipAddress());

        startHeartbeat();

        return registeredInstanceFromEureka.toServiceInstance();

    }

    private void startHeartbeat() {
        if (nonNull(heartbeatExecutor.get())) {
            shutdownHeartbeat();
        }

        var heartbeatInterval = config.getHeartbeatInvervalInSeconds();
        LOG.debug("Starting heartbeat with interval {} seconds", heartbeatInterval);

        heartbeatExecutor.set(newHeartbeatExecutor());
        heartbeatExecutor.get().scheduleWithFixedDelay(new EurekaHeartbeatSender(client, this, registeredInstance.get()),
                heartbeatInterval, heartbeatInterval, TimeUnit.SECONDS);
    }

    private void shutdownHeartbeat() {
        var executor = heartbeatExecutor.get();

        if (isNull(executor)) {
            LOG.trace("Heartbeat executor was null; nothing to shut down.");
            return;
        }

        LOG.info("Shutting heartbeat executor down: {}", executor);
        var tasks = executor.shutdownNow();

        if (isNotNullOrEmpty(tasks)) {
            LOG.info("There are {} task(s) that never started for heartbeat executor: {}", tasks.size(), executor);
        }

        try {
            var terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
            LOG.info("Heartbeat executor {} terminated before timeout? {}", executor, terminated);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted waiting for termination of heartbeat executor {}", executor);
        }

        heartbeatExecutor.set(null);
    }

    private static ScheduledExecutorService newHeartbeatExecutor() {
        var threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("eureka-heartbeat-%d")
                .setDaemon(true)
                .build();
        return Executors.newScheduledThreadPool(1, threadFactory);
    }

    private void registerWithEureka(String appId, ServiceInstance serviceToRegister) {
        var eurekaInstance = EurekaInstance.fromServiceInstance(serviceToRegister)
                .withApp(appId)
                .withStatus(serviceToRegister.getStatus().name())
                .withDataCenterInfo(Map.of(
                        "name", DEFAULT_DATA_CENTER_NAME,
                        "@class", DEFAULT_DATA_CENTER_INFO_CLASS
                ))
                .withLeaseInfo(Map.of(
                        LEASE_DURATION_IN_SECONDS, config.getExpirationIntervalInSeconds(),
                        LEASE_RENEWAL_INTERVAL_IN_SECONDS, config.getHeartbeatInvervalInSeconds()
                ));

        var registrationFunction = registrationSender(appId, eurekaInstance);

        var response = registerRetryer.tryGetObject(eurekaCallRetrySupplier(registrationFunction, NO_CONTENT.getStatusCode()))
                .orElseThrow(() -> {
                    var errMsg = format("Received errors or non-204 responses on ALL %s attempts to register (via POST) with Eureka",
                            MAX_REGISTRATION_ATTEMPTS);
                    return new RegistrationException(errMsg);
                });

        LOG.info("Registration for app {} has been received by Eureka", appId);
        LOG.debug("Response from server: Status [{}], Body {}", response.getStatus(), response.readEntity(String.class));

    }

    private Function<String, Response> registrationSender(String appId, EurekaInstance candidate) {
        return eurekaUrl -> {
            try {
                return client.register(eurekaUrl, appId, candidate);
            } catch (Exception e) {
                LOG.error("Failed to register app {} with body {} to Eureka at {}", appId, candidate, eurekaUrl, e);
                return null;
            }
        };
    }

    private EurekaInstance waitForInstanceToBeRegistered(String appId, String instanceId) {
        LOG.debug("Wait for registration to show in Eureka for app {}, instance {}", appId, instanceId);

        var instanceGetterFunction = instanceRequester(appId, instanceId);
        var response = awaitRetryer.tryGetObject(eurekaCallRetrySupplier(instanceGetterFunction, OK.getStatusCode()));

        return response
                .map(resp -> resp.readEntity(new GenericType<Map<String, EurekaInstance>>(){}).get("instance"))
                .orElseThrow(() -> {
                    LOG.error("Registration failed, or there is some other problem getting app {}, instance {}", appId, instanceId);
                    var errMsg = format("Unable to obtain app %s, instance %s from Eureka during registration after %s attempts",
                            appId, instanceId, MAX_AWAIT_REGISTRATION_CONFIRMATION_TRIES);
                    return new RegistrationException(errMsg);
                });
    }

    private Function<String, Response> instanceRequester(String appId, String instanceId) {
        return eurekaUrl -> {
            try {
                return client.findInstance(eurekaUrl, appId, instanceId);
            } catch (Exception e) {
                LOG.error("Failed to get instance with appId {}, instanceId {} from Eureka at {} due to unexpected exception", appId, instanceId, eurekaUrl, e);
                return null;
            }
        };
    }

    @Override
    public ServiceInstance updateStatus(ServiceInstance.Status newStatus) {
        checkState(isRegistered(), "Can not update status before calling register");

        var instanceToUnregister = registeredInstance.get();

        var appId = instanceToUnregister.getApp();
        var instanceId = instanceToUnregister.getInstanceId();

        var response = updateStatusRetryer.tryGetObject(eurekaCallRetrySupplier(
                updateStatusSender(appId, instanceId, newStatus), OK.getStatusCode()));

        Optionals.ifPresentOrElseThrow(response,
                resp -> {
                    LOG.info("Instance with appId {}, instanceId {} has been updated successfully to status {}", appId, instanceId, newStatus);
                    registeredInstance.set(instanceToUnregister.withStatus(newStatus.name()));
                },
                () -> {
                    var msg = format("Error updating status for app {}, instance {}", appId, instanceId);
                    LOG.error(msg);
                    return new RegistrationException(msg);
                });

        return registeredInstance.get().toServiceInstance();
    }

    private Function<String, Response> updateStatusSender(String appId, String instanceId, ServiceInstance.Status newStatus) {
        return eurekaUrl -> {
            try {
                return client.updateStatus(eurekaUrl, appId, instanceId, newStatus);
            } catch (Exception e) {
                LOG.error("Failed to update status to {} for instance with appId {}, instanceId {} from Eureka at {} due to unexpected exception",
                        newStatus, appId, instanceId, eurekaUrl, e);
                return null;
            }
        };
    }

    @Override
    public void unregister() {
        shutdownHeartbeat();

        if (isNotRegistered()) {
            LOG.warn("Ignoring un-register request because not currently registered (call register first)");
            return;
        }

        unregisterFromEureka();
    }

    private void unregisterFromEureka() {
        var instanceToUnregister = registeredInstance.get();

        var appId = instanceToUnregister.getApp();
        var instanceId = instanceToUnregister.getInstanceId();

        var response = unregisterRetryer.tryGetObject(eurekaCallRetrySupplier(
                unregisterSender(appId, instanceId), OK.getStatusCode()));

        Optionals.ifPresentOrElseThrow(response,
                resp -> {
                    LOG.info("Instance with appId {}, instanceId {} has been unregistered successfully", appId, instanceId);
                    registeredInstance.set(null);
                },
                () -> {
                    var msg = format("Error un-registering app {}, instance {}", appId, instanceId);
                    LOG.error(msg);
                    return new RegistrationException(msg);
                });
    }

    private Function<String, Response> unregisterSender(String appId, String instanceId) {
        return eurekaUrl -> {
            try {
                return client.unregister(eurekaUrl, appId, instanceId);
            } catch (Exception e) {
                LOG.error("Failed to unregister instance with appId {}, instanceId {} from Eureka at {} due to unexpected exception", appId, instanceId, eurekaUrl, e);
                return null;
            }
        };
    }

    private boolean isRegistered() {
        return nonNull(registeredInstance.get());
    }

    private boolean isNotRegistered() {
        return !isRegistered();
    }

    private Supplier<Response> eurekaCallRetrySupplier(Function<String, Response> restCallFunction, int successfulStatusCode) {
        return () -> {
            var eurekaUrl = urlProvider.getCurrentEurekaUrl();
            LOG.debug("Attempting a call to Eureka");

            var response = restCallFunction.apply(eurekaUrl);

            if (isNull(response)) {
                urlProvider.getNextEurekaUrl();
                LOG.error("Call to Eureka failed. See previous error for details");
                return null;
            }

            if (successfulStatusCode == response.getStatus()) {
                return response;
            }

            urlProvider.getNextEurekaUrl();
            LOG.error("HTTP {} - Call to Eureka at {} failed to respond successfully. Response body: {}",
                    response.getStatus(), eurekaUrl, response.readEntity(String.class));
            return null;
        };
    }

    void clearRegisteredInstance() {
        registeredInstance.set(null);
    }

}
