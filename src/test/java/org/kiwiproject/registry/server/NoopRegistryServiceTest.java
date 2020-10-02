package org.kiwiproject.registry.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.kiwiproject.collect.KiwiLists.first;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.util.ServiceInfoHelper;

@DisplayName("NoopRegistryService")
class NoopRegistryServiceTest {

    private NoopRegistryService registryService;

    @BeforeEach
    void setUp() {
        registryService = new NoopRegistryService();
    }

    @Nested
    class CreateCandidate {

        @Test
        void shouldCreateACandidateFromServiceInfo() {
            var serviceInfo = ServiceInfoHelper.buildTestServiceInfo();

            var instanceCandidate = registryService.createCandidateFrom(serviceInfo);

            assertThat(instanceCandidate.getServiceName()).isEqualTo(serviceInfo.getName());
            assertThat(instanceCandidate.getHostName()).isEqualTo(serviceInfo.getHostname());
            assertThat(instanceCandidate.getIp()).isEqualTo(serviceInfo.getIp());
            assertThat(instanceCandidate.getVersion()).isEqualTo(serviceInfo.getVersion());

            var firstPort = first(serviceInfo.getPorts());
            assertThat(instanceCandidate.getPorts())
                    .extracting(Port::getNumber, Port::getSecure, Port::getType)
                    .containsExactly(tuple(firstPort.getNumber(), firstPort.getSecure(), firstPort.getType()));
        }

    }

    @Nested
    class Register {

        private ServiceInstance instanceCandidate;

        @BeforeEach
        void setUp() {
            instanceCandidate = ServiceInstance.builder().serviceName("test-service").status(ServiceInstance.Status.STARTING).build();
        }

        @Test
        void shouldSetStatusAndReturnNewInstance() {
            var registeredInstance = registryService.register(instanceCandidate);

            assertThat(registeredInstance).isNotSameAs(instanceCandidate);
            assertThat(registeredInstance.getStatus()).isEqualTo(ServiceInstance.Status.UP);
        }

        @Test
        void shouldReturnTheSameDummyInstanceIfReregistered() {
            var firstRegisteredInstance = registryService.register(instanceCandidate);
            var secondRegistredInstance = registryService.register(instanceCandidate);

            assertThat(firstRegisteredInstance).isNotSameAs(instanceCandidate);
            assertThat(secondRegistredInstance).isNotSameAs(instanceCandidate);
            assertThat(firstRegisteredInstance).isSameAs(secondRegistredInstance);
        }
    }
}
