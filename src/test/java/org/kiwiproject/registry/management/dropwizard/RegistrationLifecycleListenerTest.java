package org.kiwiproject.registry.management.dropwizard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.management.RegistrationManager;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.server.RegistryService;

import java.util.ArrayList;
import java.util.List;

@DisplayName("RegistrationLifecycleListener")
class RegistrationLifecycleListenerTest {

    @Nested
    class ServerStarted {

        @Test
        void shouldCallStartOnAProvidedRegistrationManager() {
            var serviceInfo = mock(ServiceInfo.class);
            when(serviceInfo.getPorts()).thenReturn(List.of(Port.of(8080, PortType.APPLICATION, Security.SECURE)));

            var manager = mock(RegistrationManager.class);
            when(manager.getServiceInfo()).thenReturn(serviceInfo);

            var listener = new RegistrationLifecycleListener(manager);

            listener.serverStarted(mock(Server.class));

            verify(manager).start();
        }

        @Test
        void shouldCallStartOnARegistrationManagerCreatedFromServiceInfoAndRegistryService() {
            var info = mock(ServiceInfo.class);
            when(info.getPorts()).thenReturn(List.of(Port.of(9100, PortType.ADMIN, Security.NOT_SECURE)));

            var registryService = mock(RegistryService.class);

            var serviceInstance = ServiceInstance.builder().build();
            when(registryService.createCandidateFrom(info)).thenReturn(serviceInstance);

            var listener = new RegistrationLifecycleListener(info, registryService);

            listener.serverStarted(mock(Server.class));

            verify(registryService).createCandidateFrom(info);
            verify(registryService).register(serviceInstance);
        }

        @Test
        void shouldSetThePortWhenNotProvidedFromHttpsServer() {
            var info = createServiceInfoWithEmptyPorts();

            var manager = mock(RegistrationManager.class);
            when(manager.getServiceInfo()).thenReturn(info);

            var listener = new RegistrationLifecycleListener(manager);

            var connector = mock(ServerConnector.class);
            when(connector.getLocalPort()).thenReturn(8080);
            when(connector.getName()).thenReturn("https");
            when(connector.getHost()).thenReturn("localhost");
            when(connector.getProtocols()).thenReturn(List.of("HTTPS"));

            var server = mock(Server.class);
            when(server.getConnectors()).thenReturn(new Connector[]{connector});
            when(server.getConnectors()).thenReturn(new Connector[]{connector});

            listener.serverStarted(server);

            assertThat(info.getPorts())
                    .usingRecursiveFieldByFieldElementComparator()
                    .contains(Port.of(8080, PortType.APPLICATION, Security.SECURE));
        }

        @Test
        void shouldSetThePortWhenNotProvidedFromHttpServer() {
            var info = createServiceInfoWithEmptyPorts();

            var manager = mock(RegistrationManager.class);
            when(manager.getServiceInfo()).thenReturn(info);

            var listener = new RegistrationLifecycleListener(manager);

            var connector = mock(ServerConnector.class);
            when(connector.getLocalPort()).thenReturn(8080);
            when(connector.getName()).thenReturn("http");
            when(connector.getHost()).thenReturn("localhost");
            when(connector.getProtocols()).thenReturn(List.of("HTTP"));

            var server = mock(Server.class);
            when(server.getConnectors()).thenReturn(new Connector[]{connector});
            when(server.getConnectors()).thenReturn(new Connector[]{connector});

            listener.serverStarted(server);

            assertThat(info.getPorts())
                    .usingRecursiveFieldByFieldElementComparator()
                    .contains(Port.of(8080, PortType.APPLICATION, Security.NOT_SECURE));
        }

        private ServiceInfo createServiceInfoWithEmptyPorts() {
            return new ServiceInfo() {
                private final List<Port> ports = new ArrayList<>();

                @Override
                public String getName() {
                    return null;
                }

                @Override
                public String getHostname() {
                    return null;
                }

                @Override
                public String getIp() {
                    return null;
                }

                @Override
                public List<Port> getPorts() {
                    return ports;
                }

                @Override
                public String getVersion() {
                    return null;
                }
            };
        }
    }

    @Nested
    class LifeCycleStopping {

        @Test
        void shouldSendARequestToUnregister() {
            var manager = mock(RegistrationManager.class);
            var listener = new RegistrationLifecycleListener(manager);

            listener.lifeCycleStopping(mock(LifeCycle.class));

            verify(manager).stop();
        }

    }
}
