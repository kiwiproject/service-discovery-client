package org.kiwiproject.registry.eureka.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.collect.KiwiLists.isNotNullOrEmpty;
import static org.kiwiproject.registry.eureka.util.EurekaTestDataHelper.newEurekaContainer;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
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
import org.kiwiproject.registry.eureka.util.RegisteredInstanceInfo;
import org.kiwiproject.retry.KiwiRetryerException;
import org.kiwiproject.retry.RetryException;
import org.kiwiproject.retry.WaitStrategies;
import org.kiwiproject.retry.WaitStrategy;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@DisplayName("EurekaRegistryClient")
@Testcontainers
@Slf4j
class EurekaRegistryClientTest {

    @Container
    public static final GenericContainer<?> EUREKA = newEurekaContainer(LOG);

    private EurekaRegistryClient client;
    private EurekaConfig config;
    private RegisteredInstanceInfo registeredInstanceInfo;

    @BeforeEach
    void setUp() {
        config = new EurekaConfig();
        config.setRegistryUrls(EurekaTestDataHelper.eurekaUrl(EUREKA));

        client = new EurekaRegistryClient(config, new EurekaRestClient());

        EurekaTestDataHelper.waitForEurekaToStart(config.getRegistryUrls());
        registeredInstanceInfo = EurekaTestDataHelper.registerInstanceAndAwaitVisibility(config.getRegistryUrls());
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
                var instance = client.findServiceInstanceBy(registeredInstanceInfo.vipAddress());

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
                var instanceQuery = RegistryClient.InstanceQuery.builder()
                        .serviceName(registeredInstanceInfo.vipAddress())
                        .build();
                var instance = client.findServiceInstanceBy(instanceQuery);

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
                var instance = client.findAllServiceInstancesBy(registeredInstanceInfo.vipAddress());

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
                var instanceQuery = RegistryClient.InstanceQuery.builder()
                        .serviceName(registeredInstanceInfo.vipAddress())
                        .build();
                var instance = client.findAllServiceInstancesBy(instanceQuery);

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
            var instance = client.findServiceInstanceBy(
                    registeredInstanceInfo.vipAddress(), registeredInstanceInfo.instanceId());

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
            await().pollInterval(Duration.ofMillis(500)).atMost(1, TimeUnit.MINUTES).until(() -> {
                var instances = client.retrieveAllRegisteredInstances();
                return isNotNullOrEmpty(instances);
            });

            var instances = client.retrieveAllRegisteredInstances();

            assertThat(instances).hasSize(1);

            var instance = first(instances);

            assertAll(
                    () -> assertThat(instance.getInstanceId()).isEqualTo(registeredInstanceInfo.instanceId()),
                    () -> assertThat(instance.getServiceName()).isEqualTo(registeredInstanceInfo.vipAddress())
            );
        }
    }
}
