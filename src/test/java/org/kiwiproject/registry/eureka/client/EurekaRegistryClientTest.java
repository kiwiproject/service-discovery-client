package org.kiwiproject.registry.eureka.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.WaitStrategies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.eureka.junit.EurekaServerExtension;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.eureka.common.EurekaRestClient;
import org.kiwiproject.registry.eureka.config.EurekaConfig;
import org.kiwiproject.retry.KiwiRetryerException;

import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Response;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@DisplayName("EurekaRegistryClient")
class EurekaRegistryClientTest {

    @RegisterExtension
    public static EurekaServerExtension EUREKA = new EurekaServerExtension();

    private EurekaRegistryClient client;
    private EurekaConfig config;

    @BeforeEach
    void setUp() {
        config = new EurekaConfig();
        config.setRegistryUrls("http://localhost:" + EUREKA.getPort() + "/eureka/v2");

        var realClient = new EurekaRegistryClient(config, new EurekaRestClient());
        client = spy(realClient);

        EUREKA.getEurekaServer()
                .getRegistry()
                .registerApplication("TEST-APP", "localhost", "TEST-APP", "UP");
    }

    @AfterEach
    void cleanupEureka() {
        EUREKA.getEurekaServer().getRegistry().cleanupApps();
    }

    @Nested
    class FindServiceInstanceBy {

        @Nested
        class WithServiceName {
            @Test
            void shouldReturnServiceInstanceWhenMatchFound() {
                var instance = client.findServiceInstanceBy("TEST-APP");

                assertThat(instance).isPresent();
            }

            @Test
            void shouldReturnOptionalEmptyWhenNotFound() {
                var instance = client.findServiceInstanceBy("NOOP");
                assertThat(instance).isEmpty();
            }
        }

        @Nested
        class WithQuery {
            @Test
            void shouldReturnServiceInstanceWhenMatchFound() {
                var instance = client.findServiceInstanceBy(RegistryClient.InstanceQuery.builder().serviceName("TEST-APP").build());

                assertThat(instance).isPresent();
            }

            @Test
            void shouldReturnOptionalEmptyWhenNotFound() {
                var instance = client.findServiceInstanceBy(RegistryClient.InstanceQuery.builder().serviceName("NOOP").build());
                assertThat(instance).isEmpty();
            }
        }
    }

    @Nested
    class FindAllInstancesBy {
        @Nested
        class WithServiceName {
            @Test
            void shouldReturnListOfServiceInstancesWhenMatchFound() {
                var instance = client.findAllServiceInstancesBy("TEST-APP");

                assertThat(instance).hasSize(1);
            }

            @Test
            void shouldReturnOptionalEmptyWhenNotFound() {
                var instance = client.findAllServiceInstancesBy("NOOP");
                assertThat(instance).isEmpty();
            }
        }

        @Nested
        class WithQuery {
            @Test
            void shouldReturnListOfServiceInstancesWhenMatchFound() {
                var instance = client.findAllServiceInstancesBy(RegistryClient.InstanceQuery.builder().serviceName("TEST-APP").build());

                assertThat(instance).hasSize(1);
            }

            @Test
            void shouldReturnOptionalEmptyWhenNotFound() {
                var instance = client.findAllServiceInstancesBy(RegistryClient.InstanceQuery.builder().serviceName("NOOP").build());
                assertThat(instance).isEmpty();
            }
        }
    }

    @Nested
    class FindInstanceByServiceAndInstanceId {
        @Test
        void shouldReturnServiceInstanceWhenMatchFound() {
            var instance = client.findServiceInstanceBy("TEST-APP", "localhost");

            assertThat(instance).isPresent();
        }

        @Test
        void shouldReturnOptionalEmptyWhenNotFound() {
            var instance = client.findServiceInstanceBy("NOOP", "localhost");
            assertThat(instance).isEmpty();
        }
    }

    @Nested
    class WithRetry {

        private EurekaRestClient restClient;

        @BeforeEach
        void setUp() {
            restClient = mock(EurekaRestClient.class);
            client = spy(new EurekaRegistryClient(config, restClient));
        }

        @Test
        void shouldRetryOnServerErrorException_BadGateway() {
            when(restClient.findInstancesByVipAddress(isA(String.class), isA(String.class)))
                    .thenThrow(new ServerErrorException(Response.Status.BAD_GATEWAY))
                    .thenThrow(new ServerErrorException(Response.Status.BAD_GATEWAY))
                    .thenThrow(new ServerErrorException(Response.Status.BAD_GATEWAY));

            when(client.getWaitStrategy()).thenReturn(WaitStrategies.fixedWait(100, TimeUnit.MILLISECONDS));

            assertThatThrownBy(() -> client.findAllServiceInstancesBy("my-service"))
                    .isInstanceOf(KiwiRetryerException.class)
                    .hasCauseInstanceOf(RetryException.class);

            verify(restClient, times(3)).findInstancesByVipAddress(config.getRegistryUrls(), "my-service");
        }

        @Test
        void shouldRetryOnServerErrorException_ServiceUnavailable() {
            when(restClient.findInstancesByVipAddress(isA(String.class), isA(String.class)))
                    .thenThrow(new ServerErrorException(Response.Status.SERVICE_UNAVAILABLE))
                    .thenThrow(new ServerErrorException(Response.Status.SERVICE_UNAVAILABLE))
                    .thenThrow(new ServerErrorException(Response.Status.SERVICE_UNAVAILABLE));

            when(client.getWaitStrategy()).thenReturn(WaitStrategies.fixedWait(100, TimeUnit.MILLISECONDS));

            assertThatThrownBy(() -> client.findAllServiceInstancesBy("my-service"))
                    .isInstanceOf(KiwiRetryerException.class)
                    .hasCauseInstanceOf(RetryException.class);

            verify(restClient, times(3)).findInstancesByVipAddress(config.getRegistryUrls(), "my-service");
        }

        @Test
        void shouldRetryOnServerErrorException_GatewayTimeout() {
            when(restClient.findInstancesByVipAddress(isA(String.class), isA(String.class)))
                    .thenThrow(new ServerErrorException(Response.Status.GATEWAY_TIMEOUT))
                    .thenThrow(new ServerErrorException(Response.Status.GATEWAY_TIMEOUT))
                    .thenThrow(new ServerErrorException(Response.Status.GATEWAY_TIMEOUT));

            when(client.getWaitStrategy()).thenReturn(WaitStrategies.fixedWait(100, TimeUnit.MILLISECONDS));

            assertThatThrownBy(() -> client.findAllServiceInstancesBy("my-service"))
                    .isInstanceOf(KiwiRetryerException.class)
                    .hasCauseInstanceOf(RetryException.class);

            verify(restClient, times(3)).findInstancesByVipAddress(config.getRegistryUrls(), "my-service");
        }

        @Test
        void shouldNotRetryOnServerErrorException_InternalServerError() {
            when(restClient.findInstancesByVipAddress(isA(String.class), isA(String.class)))
                    .thenThrow(new ServerErrorException(Response.Status.INTERNAL_SERVER_ERROR));

            when(client.getWaitStrategy()).thenReturn(WaitStrategies.fixedWait(100, TimeUnit.MILLISECONDS));

            assertThatThrownBy(() -> client.findAllServiceInstancesBy("my-service"))
                    .isInstanceOf(KiwiRetryerException.class)
                    .hasCauseInstanceOf(ExecutionException.class);

            verify(restClient).findInstancesByVipAddress(config.getRegistryUrls(), "my-service");
        }
    }
}
