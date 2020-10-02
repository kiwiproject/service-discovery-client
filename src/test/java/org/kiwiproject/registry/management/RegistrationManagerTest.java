package org.kiwiproject.registry.management;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.server.RegistryService;
import org.kiwiproject.registry.util.ServiceInfoHelper;

@DisplayName("RegistrationManager")
class RegistrationManagerTest {

    private RegistryService registryService;
    private ServiceInfo serviceInfo;
    private RegistrationManager manager;

    @BeforeEach
    void setUp() {
        registryService = mock(RegistryService.class);
        serviceInfo = ServiceInfoHelper.buildTestServiceInfo();
        manager = new RegistrationManager(serviceInfo, registryService);
    }

    @Nested
    class Start {

        @Test
        void shouldCreateAndAttemptToRegisterAService() {
            var serviceInstance = ServiceInstance.builder().build();
            when(registryService.createCandidateFrom(serviceInfo)).thenReturn(serviceInstance);

            manager.start();

            verify(registryService).createCandidateFrom(serviceInfo);
            verify(registryService).register(serviceInstance);
        }
    }

    @Nested
    class Stop {

        @Test
        void shouldAttemptToUnregisterAService() {
            manager.stop();

            verify(registryService).unregister();
        }
    }
}
