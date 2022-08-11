package org.kiwiproject.registry.eureka.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.kiwiproject.registry.eureka.server.EurekaHeartbeatSender.FailureHandlerResult.CANNOT_SELF_HEAL;
import static org.kiwiproject.registry.eureka.server.EurekaHeartbeatSender.FailureHandlerResult.SELF_HEALING_FAILED;
import static org.kiwiproject.registry.eureka.server.EurekaHeartbeatSender.FailureHandlerResult.SELF_HEALING_SUCCEEDED;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.eureka.common.EurekaInstance;
import org.kiwiproject.registry.eureka.common.EurekaRestClient;
import org.kiwiproject.registry.eureka.common.EurekaUrlProvider;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.util.ServiceInfoHelper;
import org.mockito.ArgumentCaptor;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

@DisplayName("EurekaHeartbeatSender")
class EurekaHeartbeatSenderTest {

    private EurekaRegistryService service;
    private EurekaHeartbeatSender sender;
    private EurekaRestClient client;

    @BeforeEach
    void setUp() {
        service = mock(EurekaRegistryService.class);
        client = mock(EurekaRestClient.class);

        var serviceInstance = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfo())
                .withStatus(ServiceInstance.Status.UP);
        var eurekaInstance = EurekaInstance.fromServiceInstance(serviceInstance).withApp("test-service-app");
        sender = new EurekaHeartbeatSender(client, service, eurekaInstance, new EurekaUrlProvider("http://localhost:8764"), () -> {});
    }

    @Nested
    class HandleHeartbeatFailuresExceededMax {

        @Test
        void shouldReturnCannotSelfHealOnConnectionError() {
            var result = sender.handleHeartbeatFailuresExceededMax(null, new ConnectException());
            assertThat(result).isEqualTo(CANNOT_SELF_HEAL);
        }

        @Test
        void shouldReturnCannotSelfHealOnSocketTimeoutError() {
            var result = sender.handleHeartbeatFailuresExceededMax(null, new SocketTimeoutException());
            assertThat(result).isEqualTo(CANNOT_SELF_HEAL);
        }

        @Test
        void shouldReturnCannotSelfHealOnNon404ResponseWithNoException() {
            var result = sender.handleHeartbeatFailuresExceededMax(Response.serverError().build(), null);
            assertThat(result).isEqualTo(CANNOT_SELF_HEAL);
        }

        @Test
        void shouldReturnSelfHealFailedOn404ResponseWithNoExceptionAndReregistrationFailed() {
            when(service.register(any(ServiceInstance.class))).thenThrow(new RuntimeException());

            var result = sender.handleHeartbeatFailuresExceededMax(Response.status(404).build(), null);
            assertThat(result).isEqualTo(SELF_HEALING_FAILED);

            verify(service).register(isA(ServiceInstance.class));
        }

        @Test
        void shouldReturnSelfHealSuccessOn404ResponseWithNoExceptionAndReregistrationSucceeds() {
            when(service.register(any(ServiceInstance.class))).thenAnswer(returnsFirstArg());

            var result = sender.handleHeartbeatFailuresExceededMax(Response.status(404).build(), null);
            assertThat(result).isEqualTo(SELF_HEALING_SUCCEEDED);

            var serviceInstanceCaptor = ArgumentCaptor.forClass(ServiceInstance.class);
            verify(service).register(serviceInstanceCaptor.capture());

            assertThat(serviceInstanceCaptor.getValue().getStatus())
                    .describedAs("Should re-register with status UP")
                    .isEqualTo(ServiceInstance.Status.UP);
        }
    }

    @Nested
    class Run {

        @Test
        void whenSendCallThrowsExceptionIncreaseFailureCounts() {
            when(client.sendHeartbeat(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("oops"));

            sender.run();

            assertThat(sender.getHeartbeatFailures()).isEqualTo(1);
            assertThat(sender.getHeartbeatFailureStartedAt()).isNotNull();
        }

        @Test
        void whenSendCallThrowsExceptionIncreaseFailureCountsAndHandleUnlikelyStateIssueWithFailureTimestamp() {
            when(client.sendHeartbeat(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("oops"));
            sender.setHeartbeatFailures(1);

            sender.run();

            assertThat(sender.getHeartbeatFailures()).isEqualTo(2);
            assertThat(sender.getHeartbeatFailureStartedAt()).isNotNull();
        }
    }

    /**
     * Since we can't (easily) change the log level while performing tests, this tests the logging that occurs on
     * heartbeat failures when the level is higher than TRACE, which we expect to be the normal case. It doesn't really
     * make any assertions, just ensures that the logging code executes without throwing exceptions, even when it
     * receives invalid arguments. Since this is a private method, that "should not" happen, but of course reality can
     * sometimes be different.
     */
    @Nested
    class LogHeartbeatFailureWithMoreExceptionInfo {

        @Test
        void shouldLogEvenWhenPassedNullArguments() {
            assertThatCode(() -> EurekaHeartbeatSender.logHeartbeatFailureWithMoreExceptionInfo(0, null, null, null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldLogWhenHeartbeatExceptionIsNull() {
            assertThatCode(() ->
                    EurekaHeartbeatSender.logHeartbeatFailureWithMoreExceptionInfo(2, "30 seconds", "404", "<no exception>", null)
            ).doesNotThrowAnyException();
        }

        @Test
        void shouldLogWhenHeartbeatExceptionIsNotNull_AndHasCause() {
            var cause = new ConnectException("Connection refused");
            var heartbeatException = new ProcessingException("java.net.ConnectException: Connection refused", cause);

            assertThatCode(() ->
                    EurekaHeartbeatSender.logHeartbeatFailureWithMoreExceptionInfo(1, "0 seconds", "<no response>", ProcessingException.class.getCanonicalName(), heartbeatException)
            ).doesNotThrowAnyException();
        }

        @Test
        void shouldLogWhenHeartbeatExceptionIsNotNull_ButHasNoCause() {
            var heartbeatException = new RuntimeException("Something went really wrong");

            assertThatCode(() ->
                    EurekaHeartbeatSender.logHeartbeatFailureWithMoreExceptionInfo(3, "60 seconds", "<no response>", RuntimeException.class.getCanonicalName(), heartbeatException)
            ).doesNotThrowAnyException();
        }
    }
}
