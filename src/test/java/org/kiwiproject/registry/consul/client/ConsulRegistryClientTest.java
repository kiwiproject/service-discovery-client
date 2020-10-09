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

@DisplayName("ConsulRegistryClient")
class ConsulRegistryClientTest {

    // NOTE: Even though this extension uses an AfterAllCallback, it can NOT be static as running all of the tests fail. I'm not sure if this is
    //       something with the extension or with the Nested test classes
    @RegisterExtension
    ConsulExtension CONSUL = new ConsulExtension();

    private ConsulRegistryClient client;

    @SuppressWarnings("UnstableApiUsage")
    @BeforeEach
    void setUp() {
        var consul = Consul.builder()
                .withHostAndPort(HostAndPort.fromParts("localhost", CONSUL.getHttpPort()))
                .build();
        client = new ConsulRegistryClient(consul, new ConsulConfig());

        consul.agentClient()
                .register(ImmutableRegistration.builder()
                        .name("APPID")
                        .id("INSTANCEID")
                        .address("localhost")
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
            var instance = client.findServiceInstanceBy("APPID", "localhost");

            assertThat(instance).isPresent();
        }

        @Test
        void shouldReturnOptionalEmptyWhenNotFound() {
            var instance = client.findServiceInstanceBy("NOOP", "localhost");
            assertThat(instance).isEmpty();
        }
    }
}
