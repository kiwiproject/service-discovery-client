package org.kiwiproject.registry.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.kiwiproject.registry.client.RegistryClient.InstanceQuery;
import org.kiwiproject.registry.consul.client.ConsulRegistryClient;
import org.kiwiproject.registry.eureka.client.EurekaRegistryClient;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.util.ServiceInfoHelper;
import org.testcontainers.shaded.com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@DisplayName("MultiRegistryClient")
class MultiRegistryClientTest {

    private EurekaRegistryClient eurekaRegistryClient;
    private ConsulRegistryClient consulRegistryClient;
    private MultiRegistryClient multiRegistryClient;

    @BeforeEach
    void setUp() {
        eurekaRegistryClient = mock(EurekaRegistryClient.class);
        consulRegistryClient = mock(ConsulRegistryClient.class);
        multiRegistryClient = new MultiRegistryClient(List.of(eurekaRegistryClient, consulRegistryClient));
    }

    @Nested
    class Constructor {

        @ParameterizedTest
        @NullAndEmptySource
        void shouldRequireAtLeastOneRegistryClient(List<RegistryClient> registryClients) {
            assertThatIllegalArgumentException().isThrownBy(() -> new MultiRegistryClient(registryClients))
                    .withMessage("registryClients must not be null or empty");
        }

        @Test
        void shouldCreateNewInstance() {
            var registryClients = List.of(consulRegistryClient, new NoOpRegistryClient());
            var registryClient = new MultiRegistryClient(registryClients);

            assertThat(registryClient.getRegistryClients())
                    .isUnmodifiable()
                    .isEqualTo(registryClients);
        }

        @Test
        void shouldCreateDefensiveCopyOfMutableListArgument() {
            var registryClients = Lists.newArrayList(eurekaRegistryClient, consulRegistryClient, new NoOpRegistryClient());
            var registryClient = new MultiRegistryClient(registryClients);

            assertThat(registryClient.getRegistryClients())
                    .isEqualTo(registryClients)
                    .isNotSameAs(registryClients);
        }
    }

    @Nested
    class FactoryMethod {

        @Test
        void shouldRequireAtLeastOneRegistryClient() {
            assertThatIllegalArgumentException().isThrownBy(MultiRegistryClient::of)
                    .withMessage("at least one RegistryClient must be provided");
        }

        @Test
        void shouldNotAllowDumbCallersToPassExplicitNullToVarargs() {
            assertThatIllegalArgumentException().isThrownBy(() -> MultiRegistryClient.of((RegistryClient[]) null))
                    .withMessage("registryClients varargs must not be null");
        }

        @Test
        void shouldCreateNewInstance() {
            var noOpRegistryClient = new NoOpRegistryClient();
            var registryClient = MultiRegistryClient.of(consulRegistryClient, noOpRegistryClient);

            assertThat(registryClient.getRegistryClients())
                    .isUnmodifiable()
                    .containsExactly(consulRegistryClient, noOpRegistryClient);
        }
    }

    @Nested
    class FindAllServiceInstances {

        @Test
        void shouldReturnServiceInstances_FromAllRegistryClients() {
            var eurekaServices = List.of(
                newServiceInstance("order-service", "server-1"),
                newServiceInstance("order-service", "server-2")
            );

            var consulServices = List.of(
                newServiceInstance("order-service", "server-1"),
                newServiceInstance("order-service", "server-2")
            );

            when(eurekaRegistryClient.findAllServiceInstancesBy(any(InstanceQuery.class))).thenReturn(eurekaServices);
            when(consulRegistryClient.findAllServiceInstancesBy(any(InstanceQuery.class))).thenReturn(consulServices);

            var query = InstanceQuery.builder().build();
            var services = multiRegistryClient.findAllServiceInstancesBy(query);

            var expectedServices = new ArrayList<ServiceInstance>();
            expectedServices.addAll(eurekaServices);
            expectedServices.addAll(consulServices);

            assertThat(services).containsExactlyElementsOf(expectedServices);

            verify(eurekaRegistryClient).findAllServiceInstancesBy(query);
            verify(consulRegistryClient).findAllServiceInstancesBy(query);
            verifyNoMoreInteractions(eurekaRegistryClient, consulRegistryClient);
        }

        @Test
        void shouldReturnServiceInstances_FromNextRegistryClient_WhenFirstOneDoesNotFindAnything() {
            var consulServices = List.of(
                newServiceInstance("invoice-service", "server-1"),
                newServiceInstance("invoice-service", "server-2")
            );

            when(eurekaRegistryClient.findAllServiceInstancesBy(any(InstanceQuery.class))).thenReturn(List.of());
            when(consulRegistryClient.findAllServiceInstancesBy(any(InstanceQuery.class))).thenReturn(consulServices);

            var query = InstanceQuery.builder().build();
            var services = multiRegistryClient.findAllServiceInstancesBy(query);

            assertThat(services).containsExactlyElementsOf(consulServices);

            verify(eurekaRegistryClient).findAllServiceInstancesBy(query);
            verify(consulRegistryClient).findAllServiceInstancesBy(query);
            verifyNoMoreInteractions(eurekaRegistryClient, consulRegistryClient);
        }

        @Test
        void shouldReturnEmptyList_WhenNoRegistryFindsAnyServices() {
            when(eurekaRegistryClient.findAllServiceInstancesBy(any(InstanceQuery.class))).thenReturn(List.of());
            when(consulRegistryClient.findAllServiceInstancesBy(any(InstanceQuery.class))).thenReturn(List.of());

            var query = InstanceQuery.builder().build();
            var services = multiRegistryClient.findAllServiceInstancesBy(query);

            assertThat(services).isEmpty();

            verify(eurekaRegistryClient).findAllServiceInstancesBy(query);
            verify(consulRegistryClient).findAllServiceInstancesBy(query);
            verifyNoMoreInteractions(eurekaRegistryClient, consulRegistryClient);
        }
    }

    @Nested
    class FindServiceInstanceBy {

        @Test
        void shouldReturnServiceInstance_FromFirstRegistryClientWhenFound() {
            var instance = ServiceInstance.builder().build();
            when(eurekaRegistryClient.findServiceInstanceBy(anyString(), anyString()))
                    .thenReturn(Optional.of(instance));

            var serviceOptional = multiRegistryClient.findServiceInstanceBy("test-service", "instance-id");

            assertThat(serviceOptional).contains(instance);

            verify(eurekaRegistryClient).findServiceInstanceBy("test-service", "instance-id");
            verifyNoMoreInteractions(eurekaRegistryClient);
            verifyNoInteractions(consulRegistryClient);
        }

        @Test
        void shouldReturnServiceInstance_FromNextRegistry_WhenFirstOneDoesNotFindAnything() {
            var instance = ServiceInstance.builder().build();
            when(eurekaRegistryClient.findServiceInstanceBy(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(consulRegistryClient.findServiceInstanceBy(anyString(), anyString()))
                    .thenReturn(Optional.of(instance));

            var serviceOptional = multiRegistryClient.findServiceInstanceBy("test-service", "instance-id");

            assertThat(serviceOptional).contains(instance);

            verify(eurekaRegistryClient).findServiceInstanceBy("test-service", "instance-id");
            verify(consulRegistryClient).findServiceInstanceBy("test-service", "instance-id");
            verifyNoMoreInteractions(eurekaRegistryClient, consulRegistryClient);
        }

        @Test
        void shouldReturnEmptyOptional_WhenNoRegistryFindsTheService() {
            when(eurekaRegistryClient.findServiceInstanceBy(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(consulRegistryClient.findServiceInstanceBy(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            var serviceOptional = multiRegistryClient.findServiceInstanceBy("test-service", "instance-id");

            assertThat(serviceOptional).isEmpty();

            verify(eurekaRegistryClient).findServiceInstanceBy("test-service", "instance-id");
            verify(consulRegistryClient).findServiceInstanceBy("test-service", "instance-id");
            verifyNoMoreInteractions(eurekaRegistryClient, consulRegistryClient);
        }
    }

    @Nested
    class RetrieveAllRegisteredInstances {

        @Test
        void shouldReturnRegisteredInstances_FromAllRegistryClients() {
            var eurekaServices = List.of(
                newServiceInstance("invoice-service", "server-1"),
                newServiceInstance("invoice-service", "server-2"),
                newServiceInstance("order-service", "server-1"),
                newServiceInstance("order-service", "server-2"),
                newServiceInstance("shipping-service", "server-1"),
                newServiceInstance("shipping-service", "server-2")
            );

            var consulServices = List.of(
                newServiceInstance("invoice-service", "server-1"),
                newServiceInstance("invoice-service", "server-2"),
                newServiceInstance("returns-service", "server-1"),
                newServiceInstance("returns-service", "server-2")
            );

            when(eurekaRegistryClient.retrieveAllRegisteredInstances()).thenReturn(eurekaServices);
            when(consulRegistryClient.retrieveAllRegisteredInstances()).thenReturn(consulServices);

            var services = multiRegistryClient.retrieveAllRegisteredInstances();

            var expectedServices = new ArrayList<ServiceInstance>();
            expectedServices.addAll(eurekaServices);
            expectedServices.addAll(consulServices);

            assertThat(services).containsExactlyElementsOf(expectedServices);

            verify(eurekaRegistryClient).retrieveAllRegisteredInstances();
            verify(consulRegistryClient).retrieveAllRegisteredInstances();
            verifyNoMoreInteractions(eurekaRegistryClient, consulRegistryClient);
        }

        @Test
        void shouldReturnRegisteredInstances_FromSecondRegistryClient() {
            var consulServices = List.of(
                newServiceInstance("invoice-service", "server-1"),
                newServiceInstance("invoice-service", "server-2")
            );

            when(eurekaRegistryClient.retrieveAllRegisteredInstances()).thenReturn(List.of());
            when(consulRegistryClient.retrieveAllRegisteredInstances()).thenReturn(consulServices);

            var services = multiRegistryClient.retrieveAllRegisteredInstances();

            assertThat(services).containsExactlyElementsOf(consulServices);

            verify(eurekaRegistryClient).retrieveAllRegisteredInstances();
            verify(consulRegistryClient).retrieveAllRegisteredInstances();
            verifyNoMoreInteractions(eurekaRegistryClient, consulRegistryClient);
        }

        @Test
        void shouldReturnEmptyList_WhenNoRegistryFindsAnyRegisteredInstances() {
            when(eurekaRegistryClient.retrieveAllRegisteredInstances()).thenReturn(List.of());
            when(consulRegistryClient.retrieveAllRegisteredInstances()).thenReturn(List.of());

            var services = multiRegistryClient.retrieveAllRegisteredInstances();

            assertThat(services).isEmpty();

            verify(eurekaRegistryClient).retrieveAllRegisteredInstances();
            verify(consulRegistryClient).retrieveAllRegisteredInstances();
            verifyNoMoreInteractions(eurekaRegistryClient, consulRegistryClient);
        }
    }

    private static ServiceInstance newServiceInstance(String name, String hostname) {
        return ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfo(name, hostname));
    }
}
