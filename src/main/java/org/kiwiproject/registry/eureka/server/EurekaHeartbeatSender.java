package org.kiwiproject.registry.eureka.server;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.kiwiproject.jaxrs.KiwiResponses.successful;
import static org.kiwiproject.logging.LazyLogParameterSupplier.lazy;
import static org.kiwiproject.registry.eureka.server.EurekaHeartbeatSender.FailureHandlerResult.CANNOT_SELF_HEAL;
import static org.kiwiproject.registry.eureka.server.EurekaHeartbeatSender.FailureHandlerResult.SELF_HEALING_FAILED;
import static org.kiwiproject.registry.eureka.server.EurekaHeartbeatSender.FailureHandlerResult.SELF_HEALING_SUCCEEDED;

import com.google.common.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.kiwiproject.base.KiwiThrowables;
import org.kiwiproject.registry.eureka.common.EurekaInstance;
import org.kiwiproject.registry.eureka.common.EurekaRestClient;
import org.kiwiproject.registry.eureka.common.EurekaUrlProvider;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.retry.KiwiRetryerPredicates;

import java.time.Duration;
import java.time.Instant;
import javax.annotation.Nullable;
import javax.ws.rs.core.Response;

@Slf4j
class EurekaHeartbeatSender implements Runnable {

    /**
     * Number of heartbeat failures after which we will assume something is wrong.
     */
    public static final int HEARTBEAT_FAILURE_THRESHOLD = 5;

    private final EurekaRestClient client;
    private final EurekaInstance registeredInstance;
    private final EurekaRegistryService registryService;
    private final EurekaUrlProvider urlProvider;

    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PACKAGE)
    private int heartbeatFailures;

    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PACKAGE)
    private Instant heartbeatFailureStartedAt;

    private final Runnable heartbeatSentListener;

    EurekaHeartbeatSender(EurekaRestClient client, EurekaRegistryService registryService, EurekaInstance registeredInstance, EurekaUrlProvider urlProvider, Runnable heartbeatSentListener) {
        this.client = client;
        this.registeredInstance = registeredInstance;
        this.registryService = registryService;
        this.urlProvider = urlProvider;
        this.heartbeatSentListener = heartbeatSentListener;
    }

    @Override
    public void run() {
        Response response = null;
        Exception exception = null;
        try {
            LOG.trace("Sending heartbeat at {} for appId {} and instanceId {} ({})",
                    lazy(() -> ISO_INSTANT.format(Instant.now())), registeredInstance.getApp(),
                    registeredInstance.getInstanceId(), this);

            response = client.sendHeartbeat(urlProvider.getCurrentEurekaUrl(), registeredInstance.getApp(),
                    registeredInstance.getInstanceId());
        } catch (Exception e) {
            exception = e;
        }

        if (nonNull(response) && successful(response)) {
            logRecoveryIfNecessary();
            heartbeatFailures = 0;
            heartbeatFailureStartedAt = null;
            heartbeatSentListener.run();
            return;
        }

        // Some error occurred, either an exception or maybe a 404...

        heartbeatFailures++;
        urlProvider.getNextEurekaUrl();

        if (heartbeatFailures == 1) {
            LOG.trace("Recording initial heartbeat failure date/time");
            heartbeatFailureStartedAt = Instant.now();
        } else if (heartbeatFailuresExistWithoutInitialStartTime()) {
            LOG.warn("We're in an invalid state somehow; {} heartbeatFailures but null heartbeatFailureStartedAt. Setting it to be safe...",
                    heartbeatFailures);
            heartbeatFailureStartedAt = Instant.now();
        }

        logHeartbeatFailure(response, exception);

        if (heartbeatFailures > HEARTBEAT_FAILURE_THRESHOLD) {
            LOG.error("Exceeded heartbeat failure threshold of {}. Start self-healing.",
                    HEARTBEAT_FAILURE_THRESHOLD);

            handleHeartbeatFailuresExceededMax(response, exception);
        }
    }

    private void logRecoveryIfNecessary() {
        if (heartbeatFailures > 0) {
            var duration = durationSinceFirstHeartbeatFailure();
            LOG.info("And after {} straight heartbeat failure(s) and {} away, we're back!", heartbeatFailures, duration);
        }
    }

    private boolean heartbeatFailuresExistWithoutInitialStartTime() {
        return heartbeatFailures > 1 && isNull(heartbeatFailureStartedAt);
    }

    private void logHeartbeatFailure(@Nullable Response response, @Nullable Exception heartbeatException) {
        var duration = durationSinceFirstHeartbeatFailure();
        var statusOrNoResponse = isNull(response) ? "<no response>" : String.valueOf(response.getStatus());
        var exceptionTypeOrNo = isNull(heartbeatException) ? "<no exception>" : heartbeatException.getClass().getCanonicalName();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Heartbeat to Eureka failed. ({} failure(s) in a row, elapsed time {}, response status: {}, exception: {})",
                    heartbeatFailures, duration, statusOrNoResponse, exceptionTypeOrNo, heartbeatException);
        } else {
            logHeartbeatFailureWithMoreExceptionInfo(heartbeatFailures, duration, statusOrNoResponse, exceptionTypeOrNo, heartbeatException);
        }
    }

    @VisibleForTesting
    static void logHeartbeatFailureWithMoreExceptionInfo(int heartbeatFailures,
                                                         String duration,
                                                         String statusOrNoResponse,
                                                         String exceptionTypeOrNo,
                                                         @Nullable Exception heartbeatException) {

        var message = KiwiThrowables.messageOfNullable(heartbeatException).orElse(null);
        var rootCause = KiwiThrowables.rootCauseOfNullable(heartbeatException).orElse(null);

        // Ignore when root cause is original exception
        var cause = (rootCause == heartbeatException) ? null : rootCause;
        var causeType = KiwiThrowables.typeOfNullable(cause).orElse(null);
        var causeMessage = KiwiThrowables.messageOfNullable(cause).orElse(null);

        LOG.warn("Heartbeat to Eureka failed ({} failure(s) in a row, elapsed time {}, response status: {}," +
                        " [exception: {}, message: {}], [root cause: {}, message: {}])",
                heartbeatFailures, duration, statusOrNoResponse, exceptionTypeOrNo, message, causeType, causeMessage);
    }

    private String durationSinceFirstHeartbeatFailure() {
        checkNotNull(heartbeatFailureStartedAt, "heartbeatFailureStartedAt should not be null here, but it was");

        var duration = Duration.between(heartbeatFailureStartedAt, Instant.now());
        return DurationFormatUtils.formatDurationWords(duration.toMillis(), true, true);
    }

    enum FailureHandlerResult {
        CANNOT_SELF_HEAL,
        SELF_HEALING_FAILED,
        SELF_HEALING_SUCCEEDED
    }

    @VisibleForTesting
    FailureHandlerResult handleHeartbeatFailuresExceededMax(Response response, Exception exception) {
        // To be conservative, if we have failed to heartbeat beyond the threshold number of heartbeats,
        // then consider ourselves as no longer registered.
        LOG.warn("Heartbeat failure threshold exceeded, so marking as no longer registered");
        registryService.clearRegisteredInstance();

        if (isCannotConnect(exception)) {
            LOG.error("Received ConnectException, indicating a network partition. Cannot self-heal right now.");
            return CANNOT_SELF_HEAL;
        } else if (isSocketTimeout(exception)) {
            LOG.error("Received SocketTimeoutException, indicating a (possibly temporary) network problem. Cannot self-heal right now.");
            return CANNOT_SELF_HEAL;
        } else if (receivedNotFound(response)) {
            LOG.error("Eureka reporting 404 Not Found for heartbeat. Eureka probably expired our registration. Will attempt to re-register...");
            try {
                var serviceToRegister = registeredInstance.toServiceInstance().withStatus(ServiceInstance.Status.UP);
                var registeredService = registryService.register(serviceToRegister);
                LOG.info("Self-healing complete. Re-registered {} on {} with Eureka as app {}",
                        registeredService.getServiceName(),
                        registeredService.getHostName(),
                        registryService.getRegisteredAppOrNull());
                return SELF_HEALING_SUCCEEDED;
            } catch (Exception e) {
                LOG.error("Error re-registering app {}. Self-healing failed.", registeredInstance.getApp(), e);
                return SELF_HEALING_FAILED;
            }
        }

        LOG.error("Able to connect to Eureka, but receiving unknown error. Cannot self-heal right now.", exception);
        return CANNOT_SELF_HEAL;
    }

    private static boolean isCannotConnect(@Nullable Exception exception) {
        return KiwiRetryerPredicates.CONNECTION_ERROR.test(exception);
    }

    private static boolean isSocketTimeout(@Nullable Exception exception) {
        return KiwiRetryerPredicates.SOCKET_TIMEOUT.test(exception);
    }

    private static boolean receivedNotFound(@Nullable Response response) {
        return nonNull(response) && response.getStatus() == 404;
    }
}
