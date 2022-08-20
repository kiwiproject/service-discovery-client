package org.kiwiproject.registry.eureka.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.registry.eureka.util.EurekaTestDataHelper.eurekaImage;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.eureka.common.EurekaRestClient;
import org.kiwiproject.registry.eureka.config.EurekaConfig;
import org.kiwiproject.registry.eureka.util.EurekaTestDataHelper;
import org.kiwiproject.retry.KiwiRetryerException;
import org.kiwiproject.retry.RetryException;
import org.kiwiproject.retry.WaitStrategies;
import org.kiwiproject.retry.WaitStrategy;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

@DisplayName("EurekaRegistryClient")
@Testcontainers
@Slf4j
class EurekaRegistryClientTest {

    @Container
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final GenericContainer EUREKA = new GenericContainer(eurekaImage())
            .withExposedPorts(8080)
            .withLogConsumer(new Slf4jLogConsumer(LOG));

    private EurekaRegistryClient client;
    private EurekaConfig config;

    @BeforeEach
    void setUp() {
        config = new EurekaConfig();
        config.setRegistryUrls(EurekaTestDataHelper.eurekaUrl(EUREKA));

        client = new EurekaRegistryClient(config, new EurekaRestClient());

        EurekaTestDataHelper.waitForEurekaToStart(config.getRegistryUrls());
        EurekaTestDataHelper.loadInstanceAndWaitForRegistration(config.getRegistryUrls());
    }

    @AfterEach
    void cleanupEureka() {
        EurekaTestDataHelper.clearAllInstances(config.getRegistryUrls());
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
            client = new EurekaRegistryClient(config, restClient) {
                @Override
                WaitStrategy getWaitStrategy() {
                    return WaitStrategies.noWait();
                }
            };
        }

        @Test
        void shouldRetryOnServerErrorException_BadGateway() {
            when(restClient.findInstancesByVipAddress(isA(String.class), isA(String.class)))
                    .thenThrow(new ServerErrorException(Response.Status.BAD_GATEWAY))
                    .thenThrow(new ServerErrorException(Response.Status.BAD_GATEWAY))
                    .thenThrow(new ServerErrorException(Response.Status.BAD_GATEWAY));

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

            assertThatThrownBy(() -> client.findAllServiceInstancesBy("my-service"))
                    .isInstanceOf(KiwiRetryerException.class)
                    .hasCauseInstanceOf(RetryException.class);

            verify(restClient, times(3)).findInstancesByVipAddress(config.getRegistryUrls(), "my-service");
        }

        @Test
        void shouldRetryWhenRootCause_IsServerErrorException_WithTemporaryStatuses() {
            when(restClient.findInstancesByVipAddress(isA(String.class), isA(String.class)))
                    .thenThrow(new WebApplicationException(new ServerErrorException(Response.Status.BAD_GATEWAY)))
                    .thenThrow(new WebApplicationException(new ServerErrorException(Response.Status.SERVICE_UNAVAILABLE)))
                    .thenThrow(new WebApplicationException(new ServerErrorException(Response.Status.GATEWAY_TIMEOUT)));

            assertThatThrownBy(() -> client.findAllServiceInstancesBy("my-service"))
                    .isInstanceOf(KiwiRetryerException.class)
                    .hasCauseInstanceOf(RetryException.class);

            verify(restClient, times(3)).findInstancesByVipAddress(config.getRegistryUrls(), "my-service");
        }

        @Test
        void shouldNotRetryOnServerErrorException_InternalServerError() {
            when(restClient.findInstancesByVipAddress(isA(String.class), isA(String.class)))
                    .thenThrow(new ServerErrorException(Response.Status.INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> client.findAllServiceInstancesBy("my-service"))
                    .isInstanceOf(KiwiRetryerException.class)
                    .hasCauseInstanceOf(RetryException.class);

            verify(restClient).findInstancesByVipAddress(config.getRegistryUrls(), "my-service");
        }

        @Test
        void shouldNotRetryWhenRootCause_IsServerErrorException_WithStatusInternalServerError() {
            when(restClient.findInstancesByVipAddress(isA(String.class), isA(String.class)))
                    .thenThrow(new WebApplicationException(new ServerErrorException(Response.Status.INTERNAL_SERVER_ERROR)));

            assertThatThrownBy(() -> client.findAllServiceInstancesBy("my-service"))
                    .isInstanceOf(KiwiRetryerException.class)
                    .hasCauseInstanceOf(RetryException.class);

            verify(restClient).findInstancesByVipAddress(config.getRegistryUrls(), "my-service");
        }

        @Test
        void shouldNotRetryWhenNeitherExceptionNoRootCauseAreServerErrorException() {
            when(restClient.findInstancesByVipAddress(isA(String.class), isA(String.class)))
                    .thenThrow(new ProcessingException("Something unexpected happened..."));

            assertThatThrownBy(() -> client.findAllServiceInstancesBy("my-service"))
                    .isInstanceOf(KiwiRetryerException.class)
                    .hasCauseInstanceOf(RetryException.class);

            verify(restClient).findInstancesByVipAddress(config.getRegistryUrls(), "my-service");
        }
    }

    @Nested
    class RetrieveAllRegisteredInstances {
        @Test
        void shouldReturnListOfServiceInstancesWhenFound() {
            var instances = client.retrieveAllRegisteredInstances();

            assertThat(instances).hasSize(1);
        }
    }
}
