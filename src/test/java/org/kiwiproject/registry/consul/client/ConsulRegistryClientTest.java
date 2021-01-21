package org.kiwiproject.registry.consul.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.common.net.HostAndPort;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.pszymczyk.consul.junit.ConsulExtension;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.consul.config.ConsulConfig;
import org.kiwiproject.registry.consul.util.ConsulStarterHelper;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance.Status;
import org.kiwiproject.registry.model.ServicePaths;

import java.util.List;
import java.util.Map;

@DisplayName("ConsulRegistryClient")
@ExtendWith(SoftAssertionsExtension.class)
class ConsulRegistryClientTest {

    // NOTE: Even though this extension uses an AfterAllCallback, it can NOT be static as running all of the tests fail. I'm not sure if this is
    //       something with the extension or with the Nested test classes
    @RegisterExtension
    final ConsulExtension consulExtension = new ConsulExtension(ConsulStarterHelper.buildStarterConfigWithEnvironment());

    private ConsulRegistryClient client;
    private ConsulConfig config;

    @SuppressWarnings("UnstableApiUsage")
    @BeforeEach
    void setUp() {
        var consul = Consul.builder()
                .withHostAndPort(HostAndPort.fromParts("localhost", consulExtension.getHttpPort()))
                .build();

        config = new ConsulConfig();
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
    class WithDomainOverride {
        @Test
        void shouldChangeTheDomainInAddress() {
            config.setDomainOverride("test");
            var instance = client.findServiceInstanceBy("APPID");

            assertThat(instance).isPresent();

            var serviceInstance = instance.get();
            assertThat(serviceInstance.getHostName()).isEqualTo("localhost.test");
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
}
