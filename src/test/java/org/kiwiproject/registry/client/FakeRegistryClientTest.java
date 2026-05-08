package org.kiwiproject.registry.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.kiwiproject.collect.KiwiLists.first;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.client.RegistryClient.InstanceQuery;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.model.ServiceInstance.Status;
import org.kiwiproject.registry.model.ServicePaths;

import java.util.List;

@DisplayName("FakeRegistryClient")
class FakeRegistryClientTest {

    private FakeRegistryClient fakeClient;

    @BeforeEach
    void setUp() {
        fakeClient = new FakeRegistryClient();
    }

    @Nested
    class Constructors {

        @Test
        void defaultConstructor_shouldCreateEmptyClient() {
            assertThat(fakeClient.retrieveAllRegisteredInstances()).isEmpty();
        }

        @Test
        void listConstructor_shouldPrePopulateInstances() {
            var instance1 = newServiceInstance("order-service", "host-1", "1.0.0");
            var instance2 = newServiceInstance("invoice-service", "host-2", "2.0.0");

            var client = new FakeRegistryClient(List.of(instance1, instance2));

            assertThat(client.retrieveAllRegisteredInstances())
                    .containsExactlyInAnyOrder(instance1, instance2);
        }

        @Test
        void listConstructor_shouldRejectNullList() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FakeRegistryClient(null))
                    .withMessage("instances must not be null");
        }
    }

    @Nested
    class AddServiceInstance {

        @Test
        void shouldAddSingleInstance() {
            var instance = newServiceInstance("order-service", "host-1", "1.0.0");
            fakeClient.addServiceInstance(instance);

            assertThat(fakeClient.findAllServiceInstancesBy("order-service")).containsExactly(instance);
        }

        @Test
        void shouldAddMultipleInstancesForSameService() {
            var instance1 = newServiceInstance("order-service", "host-1", "1.0.0");
            var instance2 = newServiceInstance("order-service", "host-2", "1.0.0");
            fakeClient.addServiceInstance(instance1);
            fakeClient.addServiceInstance(instance2);

            assertThat(fakeClient.findAllServiceInstancesBy("order-service"))
                    .containsExactly(instance1, instance2);
        }

        @Test
        void shouldRejectNullInstance() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> fakeClient.addServiceInstance(null))
                    .withMessage("instance must not be null");
        }
    }

    @Nested
    class AddServiceInstances {

        @Test
        void shouldAddAllInstances() {
            var instance1 = newServiceInstance("order-service", "host-1", "1.0.0");
            var instance2 = newServiceInstance("invoice-service", "host-2", "2.0.0");
            fakeClient.addServiceInstances(List.of(instance1, instance2));

            assertThat(fakeClient.retrieveAllRegisteredInstances())
                    .containsExactlyInAnyOrder(instance1, instance2);
        }

        @Test
        void shouldRejectNullList() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> fakeClient.addServiceInstances(null))
                    .withMessage("instances must not be null");
        }
    }

    @Nested
    class FindServiceInstanceByNameAndId {

        @Test
        void shouldReturnInstance_WhenServiceNameAndIdMatch() {
            var instance = newServiceInstance("order-service", "host-1", "1.0.0", "instance-42");
            fakeClient.addServiceInstance(instance);

            assertThat(fakeClient.findServiceInstanceBy("order-service", "instance-42"))
                    .contains(instance);
        }

        @Test
        void shouldReturnEmpty_WhenServiceNameDoesNotMatch() {
            fakeClient.addServiceInstance(newServiceInstance("order-service", "host-1", "1.0.0", "instance-42"));

            assertThat(fakeClient.findServiceInstanceBy("unknown-service", "instance-42")).isEmpty();
        }

        @Test
        void shouldReturnEmpty_WhenInstanceIdDoesNotMatch() {
            fakeClient.addServiceInstance(newServiceInstance("order-service", "host-1", "1.0.0", "instance-42"));

            assertThat(fakeClient.findServiceInstanceBy("order-service", "wrong-id")).isEmpty();
        }

        @Test
        void shouldReturnEmpty_WhenNoInstancesRegistered() {
            assertThat(fakeClient.findServiceInstanceBy("order-service", "instance-1")).isEmpty();
        }

        @Test
        void shouldRejectNullInstanceId() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> fakeClient.findServiceInstanceBy("order-service", null))
                    .withMessage("instanceId must not be null");
        }
    }

    @Nested
    class FindServiceInstanceByName {

        @Test
        void shouldReturnAnInstance_WhenServiceNameMatches() {
            var instance = newServiceInstance("order-service", "host-1", "1.0.0");
            fakeClient.addServiceInstance(instance);

            assertThat(fakeClient.findServiceInstanceBy("order-service")).contains(instance);
        }

        @Test
        void shouldReturnEmpty_WhenServiceNameDoesNotMatch() {
            fakeClient.addServiceInstance(newServiceInstance("order-service", "host-1", "1.0.0"));

            assertThat(fakeClient.findServiceInstanceBy("unknown-service")).isEmpty();
        }

        @Test
        void shouldReturnEmpty_WhenNoInstancesRegistered() {
            assertThat(fakeClient.findServiceInstanceBy("order-service")).isEmpty();
        }
    }

    @Nested
    class FindAllServiceInstancesByQuery {

        @Test
        void shouldReturnAllMatchingInstances_WhenNoVersionPredicates() {
            var instance1 = newServiceInstance("order-service", "host-1", "1.0.0");
            var instance2 = newServiceInstance("order-service", "host-2", "2.0.0");
            fakeClient.addServiceInstances(List.of(instance1, instance2));

            var query = InstanceQuery.builder().serviceName("order-service").build();
            assertThat(fakeClient.findAllServiceInstancesBy(query))
                    .containsExactly(instance1, instance2);
        }

        @Test
        void shouldReturnEmpty_WhenServiceNotRegistered() {
            var query = InstanceQuery.builder().serviceName("unknown-service").build();
            assertThat(fakeClient.findAllServiceInstancesBy(query)).isEmpty();
        }

        @Test
        void shouldFilterByMinimumVersion() {
            var v1Instance = newServiceInstance("order-service", "host-1", "1.0.0");
            var v2Instance = newServiceInstance("order-service", "host-2", "2.0.0");
            fakeClient.addServiceInstances(List.of(v1Instance, v2Instance));

            var query = InstanceQuery.builder()
                    .serviceName("order-service")
                    .minimumVersion("1.5.0")
                    .build();

            assertThat(fakeClient.findAllServiceInstancesBy(query)).containsExactly(v2Instance);
        }

        @Test
        void shouldFilterByPreferredVersion() {
            var v1Instance = newServiceInstance("order-service", "host-1", "1.0.0");
            var v2Instance = newServiceInstance("order-service", "host-2", "2.0.0");
            fakeClient.addServiceInstances(List.of(v1Instance, v2Instance));

            var query = InstanceQuery.builder()
                    .serviceName("order-service")
                    .preferredVersion("1.0.0")
                    .build();

            assertThat(fakeClient.findAllServiceInstancesBy(query)).containsExactly(v1Instance);
        }

        @Test
        void shouldNotReturnInstancesFromOtherServices() {
            fakeClient.addServiceInstance(newServiceInstance("order-service", "host-1", "1.0.0"));
            fakeClient.addServiceInstance(newServiceInstance("invoice-service", "host-2", "1.0.0"));

            var query = InstanceQuery.builder().serviceName("order-service").build();
            var results = fakeClient.findAllServiceInstancesBy(query);

            assertThat(results).hasSize(1);
            assertThat(first(results).getServiceName()).isEqualTo("order-service");
        }
    }

    @Nested
    class RetrieveAllRegisteredInstances {

        @Test
        void shouldReturnAllInstancesAcrossAllServices() {
            var instance1 = newServiceInstance("order-service", "host-1", "1.0.0");
            var instance2 = newServiceInstance("order-service", "host-2", "1.0.0");
            var instance3 = newServiceInstance("invoice-service", "host-3", "2.0.0");
            fakeClient.addServiceInstances(List.of(instance1, instance2, instance3));

            assertThat(fakeClient.retrieveAllRegisteredInstances())
                    .containsExactlyInAnyOrder(instance1, instance2, instance3);
        }

        @Test
        void shouldReturnEmptyList_WhenNoInstancesRegistered() {
            assertThat(fakeClient.retrieveAllRegisteredInstances()).isEmpty();
        }
    }

    private static ServiceInstance newServiceInstance(String serviceName, String hostName, String version) {
        return newServiceInstance(serviceName, hostName, version, null);
    }

    private static ServiceInstance newServiceInstance(String serviceName, String hostName, String version,
                                                       String instanceId) {
        return ServiceInstance.builder()
                .serviceName(serviceName)
                .hostName(hostName)
                .ip("127.0.0.1")
                .instanceId(instanceId)
                .version(version)
                .status(Status.UP)
                .ports(List.of(Port.of(8080, PortType.APPLICATION, Security.NOT_SECURE)))
                .paths(ServicePaths.builder().build())
                .build();
    }
}
