package org.kiwiproject.registry.eureka.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.registry.eureka.server.EurekaHeartbeatSender.FailureHandlerResult.CANNOT_SELF_HEAL;
import static org.kiwiproject.registry.eureka.server.EurekaHeartbeatSender.FailureHandlerResult.SELF_HEALING_FAILED;
import static org.kiwiproject.registry.eureka.server.EurekaHeartbeatSender.FailureHandlerResult.SELF_HEALING_SUCCEEDED;
import static org.mockito.ArgumentMatchers.any;
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
        var eurekaInstance = EurekaInstance.fromServiceInstance(serviceInstance);
        sender = new EurekaHeartbeatSender(client, service, eurekaInstance, new EurekaUrlProvider("http://localhost:8764"));
    }

    @Nested
    class HandleHeartbeatFailuresExceededMax {

        @Test
        void shouldReturnCannotSelfHealOnConnectionError() {
            var appId = "APPID";

            var result = sender.handleHeartbeatFailuresExceededMax(appId, null, new ConnectException());
            assertThat(result).isEqualTo(CANNOT_SELF_HEAL);
        }

        @Test
        void shouldReturnCannotSelfHealOnSocketTimeoutError() {
            var appId = "APPID";

            var result = sender.handleHeartbeatFailuresExceededMax(appId, null, new SocketTimeoutException());
            assertThat(result).isEqualTo(CANNOT_SELF_HEAL);
        }

        @Test
        void shouldReturnCannotSelfHealOnNon404ResponseWithNoException() {
            var appId = "APPID";

            var result = sender.handleHeartbeatFailuresExceededMax(appId, Response.serverError().build(), null);
            assertThat(result).isEqualTo(CANNOT_SELF_HEAL);
        }

        @Test
        void shouldReturnSelfHealFailedOn404ResponseWithNoExceptionAndReregistrationFailed() {
            var appId = "APPID";

            when(service.register(any(ServiceInstance.class))).thenThrow(new RuntimeException());

            var result = sender.handleHeartbeatFailuresExceededMax(appId, Response.status(404).build(), null);
            assertThat(result).isEqualTo(SELF_HEALING_FAILED);

            verify(service).register(isA(ServiceInstance.class));
        }

        @Test
        void shouldReturnSelfHealSuccessOn404ResponseWithNoExceptionAndReregistrationSucceeds() {
            var appId = "APPID";

            var result = sender.handleHeartbeatFailuresExceededMax(appId, Response.status(404).build(), null);
            assertThat(result).isEqualTo(SELF_HEALING_SUCCEEDED);

            verify(service).register(isA(ServiceInstance.class));
        }
    }

    @Nested
    class Run {

        @Test
        void whenSendCallThrowsExceptionIncreaseFailureCounts() {
            when(client.sendHeartbeat(isA(String.class), isA(String.class), isA(String.class))).thenThrow(new RuntimeException());

            sender.run();

            assertThat(sender.getHeartbeatFailures()).isEqualTo(1);
            assertThat(sender.getHeartbeatFailureStartedAt()).isNotNull();
        }

        @Test
        void whenSendCallThrowsExceptionIncreaseFailureCountsAndHandleUnlikelyStateIssueWithFailureTimestamp() {
            when(client.sendHeartbeat(isA(String.class), isA(String.class), isA(String.class))).thenThrow(new RuntimeException());
            sender.setHeartbeatFailures(1);

            sender.run();

            assertThat(sender.getHeartbeatFailures()).isEqualTo(2);
            assertThat(sender.getHeartbeatFailureStartedAt()).isNotNull();
        }
    }
}
