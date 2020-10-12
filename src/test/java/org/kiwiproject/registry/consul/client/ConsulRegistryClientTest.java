package org.kiwiproject.registry.consul.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.net.HostAndPort;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.pszymczyk.consul.junit.ConsulExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.consul.config.ConsulConfig;

import java.util.List;
import java.util.Map;

@DisplayName("ConsulRegistryClient")
class ConsulRegistryClientTest {

    // NOTE: Even though this extension uses an AfterAllCallback, it can NOT be static as running all of the tests fail. I'm not sure if this is
    //       something with the extension or with the Nested test classes
    @RegisterExtension
    final ConsulExtension consulExtension = new ConsulExtension();

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

        consul.agentClient()
                .register(ImmutableRegistration.builder()
                        .name("APPID")
                        .id("INSTANCEID")
                        .address("localhost.home")
                        .port(8080)
                        .tags(List.of("service-type:default", "category:CORE"))
                        .meta(Map.of(
                                "adminPort", "8081",
                                "scheme", "http"
                        ))
                        .build());
    }

    @Nested
    class FindServiceInstanceBy {

        @Nested
        class WithServiceName {
            @Test
            void shouldReturnServiceInstanceWhenMatchFound() {
                var instance = client.findServiceInstanceBy("APPID");

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

}
