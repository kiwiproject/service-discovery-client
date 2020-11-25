package org.kiwiproject.registry.management.dropwizard;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

import io.dropwizard.lifecycle.PortDescriptor;
import io.dropwizard.lifecycle.ServerLifecycleListener;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.management.RegistrationManager;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.Security;
import org.kiwiproject.registry.server.RegistryService;

import java.util.List;
import java.util.Optional;

/**
 * Listener that registers and deregisters the service based on server start up and shutdown events.
 *
 * Note: This class implements the {@link ServerLifecycleListener} which is part of Dropwizard to provide access once Dropwizard has finished starting
 * the server.  This class also extends {@link AbstractLifeCycle.AbstractLifeCycleListener} from Jetty (which Dropwizard uses under the covers) to provide
 * access when the server is starting to shutdown.  To use this class you may have to register this listener in the following ways to get both actions:
 * <p>
 * <pre>
 *  var listener = new RegistrationLifecycleListener(manager);
 *
 *  // Registers the startup with dropwizard
 *  environment.lifecycle().addServerLifecycleListener(listener);
 *
 *  // Registers the shutdown with Jetty
 *  environment.lifecycle().addLifecycleListener(listener);
 * </pre>
 */
@Slf4j
public class RegistrationLifecycleListener extends AbstractLifeCycle.AbstractLifeCycleListener implements ServerLifecycleListener {

    private final RegistrationManager registrationManager;

    /**
     * Creates a new listener with a given {@link RegistrationManager}
     *
     * @param registrationManager The {@link RegistrationManager} to use
     */
    public RegistrationLifecycleListener(RegistrationManager registrationManager) {
        this.registrationManager = registrationManager;
    }

    /**
     * Creates a new listener with a given {@link ServiceInfo} and {@link RegistryService}.  This will create the {@link RegistrationManager} that will
     * be used to register the service.
     *
     * @param serviceInfo       the {@link ServiceInfo} to use for registering the service
     * @param registryService   the {@link RegistryService} to use for sending the registration
     */
    public RegistrationLifecycleListener(ServiceInfo serviceInfo, RegistryService registryService) {
        this.registrationManager = new RegistrationManager(serviceInfo, registryService);
    }

    // This method comes from ServerLifecycleListener
    @Override
    public void serverStarted(Server server) {
        var serviceInfo = registrationManager.getServiceInfo();
        var ports = serviceInfo.getPorts();

        setPortsIfEmpty(ports, server);

        registrationManager.start();
    }

    private void setPortsIfEmpty(List<Port> ports, Server server) {
        checkArgumentNotNull(ports, "ports in ServiceInfo must not be null");

        if (ports.isEmpty()) {
            var port = getLocalPort(server);
            var adminPort = getAdminPort(server);

            var descriptors = getPortDescriptorList(server);

            findPort(port, Port.PortType.APPLICATION, descriptors).ifPresent(ports::add);
            findPort(adminPort, Port.PortType.ADMIN, descriptors).ifPresent(ports::add);
        }
    }

    private Optional<Port> findPort(int port, Port.PortType portType, List<PortDescriptor> descriptors) {
        var portProtocol = descriptors.stream()
                .filter(descriptor -> descriptor.getPort() == port)
                .map(PortDescriptor::getProtocol)
                .findFirst();

        return portProtocol.map(protocol -> Port.of(port, portType, Security.fromScheme(protocol)));
    }

    // This method comes from AbstractLifeCycle.AbstractLifeCycleListener
    @Override
    public void lifeCycleStopping(LifeCycle event) {
        try {
            registrationManager.stop();
        } catch (Exception e) {
            LOG.error("Service could not be de-registered It will need to expire or be manually de-registered.", e);
        }
    }
}
