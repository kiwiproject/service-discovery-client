package org.kiwiproject.registry.consul.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;
import static org.kiwiproject.registry.consul.util.ConsulTestcontainers.consulHostAndPort;
import static org.kiwiproject.registry.consul.util.ConsulTestcontainers.newConsulContainer;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.model.agent.ImmutableRegistration;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.consul.config.ConsulConfig;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance.Status;
import org.kiwiproject.registry.model.ServicePaths;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

@DisplayName("ConsulRegistryClient")
@Testcontainers
@ExtendWith(SoftAssertionsExtension.class)
class ConsulRegistryClientTest {

    @Container
    public static final ConsulContainer CONSUL = newConsulContainer();

    private ConsulRegistryClient client;

    @BeforeEach
    void setUp() {
        var consulHostAndPort = consulHostAndPort(CONSUL);

        var consul = Consul.builder()
                .withHostAndPort(consulHostAndPort)
                .build();

        var config = new ConsulConfig();
        client = new ConsulRegistryClient(consul, config);

        var now = System.currentTimeMillis();
        consul.agentClient()
                .register(ImmutableRegistration.builder()
                        .name("APPID")
                        .id("INSTANCEID")
                        .address("localhost.home")
                        .port(8080)
                        .tags(List.of("service-type:default", "category:CORE"))
                        .meta(Map.of(
                                "adminPort", "8081",
                                "scheme", "http",
                                "serviceUpTimestamp", Long.toString(now),
                                "commitRef", "abcdef",
                                "description", "instance id service",
                                "version", "42.0.0-SNAPSHOT",
                                "ipAddress", "127.0.0.1",
                                "homePagePath", "/api",
                                "statusPath", "/status",
                                "healthCheckPath", "/health"
                        ))
                        .build());
    }

    @Nested
    class FindServiceInstanceBy {

        @Nested
        class WithServiceName {
            @Test
            void shouldReturnServiceInstanceWhenMatchFound(SoftAssertions softly) {
                var optionalServiceInstance = client.findServiceInstanceBy("APPID");

                assertThat(optionalServiceInstance).isPresent();

                var instance = optionalServiceInstance.get();

                softly.assertThat(instance.getInstanceId()).isNotNull();
                softly.assertThat(instance.getServiceName()).isEqualTo("APPID");
                softly.assertThat(instance.getHostName()).isEqualTo("localhost.home");
                softly.assertThat(instance.getCommitRef()).isEqualTo("abcdef");
                softly.assertThat(instance.getDescription()).isEqualTo("instance id service");
                softly.assertThat(instance.getVersion()).isEqualTo("42.0.0-SNAPSHOT");
                softly.assertThat(instance.getStatus()).isEqualTo(Status.UP);
                softly.assertThat(instance.getUpSince()).isNotNull();
                softly.assertThat(instance.getUpSinceMillis()).isGreaterThan(0);

                softly.assertThat(instance.getPorts())
                        .extracting("number", "secure", "type")
                        .containsExactlyInAnyOrder(
                                tuple(8080, Port.Security.NOT_SECURE, Port.PortType.APPLICATION),
                                tuple(8081, Port.Security.NOT_SECURE, Port.PortType.ADMIN)
                        );

                softly.assertThat(instance.getPaths())
                        .usingRecursiveComparison()
                        .isEqualTo(ServicePaths.builder()
                                .homePagePath("/api")
                                .statusPath("/status")
                                .healthCheckPath("/health")
                                .build());

                softly.assertThat(instance.getNativeRegistryData()).isEmpty();
                softly.assertThat(instance.getMetadata()).containsEntry("registryType", "CONSUL");
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
                var instance = client.findServiceInstanceBy(RegistryClient.InstanceQuery.builder().serviceName("APPID").build());

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
                var instance = client.findAllServiceInstancesBy("APPID");

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
                var instance = client.findAllServiceInstancesBy(RegistryClient.InstanceQuery.builder().serviceName("APPID").build());

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
            var instance = client.findServiceInstanceBy("APPID", "INSTANCEID");

            assertThat(instance).isPresent();
        }

        @Test
        void shouldReturnOptionalEmptyWhenNotFound() {
            var instance = client.findServiceInstanceBy("NOOP", "INSTANCEID");
            assertThat(instance).isEmpty();
        }
    }

    @Nested
    class RetrieveAllRegisteredInstances {

        @Test
        void shouldReturnListOfAllServiceInstancesWhenFound() {
            var instances = client.retrieveAllRegisteredInstances();

            assertThat(instances)
                    .extracting("serviceName")
                    .contains("APPID", "consul");
        }
    }

    @Nested
    class InternalMethods {

        @Test
        void getAdminPortNumberOrThrow_shouldThrowIllegalState_whenNotANumber() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> ConsulRegistryClient.getAdminPortNumberOrThrow(Map.of("adminPort", "Foo")))
                    .withMessage("adminPort is not a number")
                    .withCauseExactlyInstanceOf(NumberFormatException.class);
        }

        @Test
        void getServiceUpTimestampOrThrow_shouldThrowIllegalState_whenNotANumber() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> ConsulRegistryClient.getServiceUpTimestampOrThrow(Map.of("serviceUpTimestamp", "Bar")))
                    .withMessage("serviceUpTimestamp is not a number")
                    .withCauseExactlyInstanceOf(NumberFormatException.class);
        }
    }
}
