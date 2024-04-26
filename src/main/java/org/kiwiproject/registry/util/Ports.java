package org.kiwiproject.registry.util;

import static com.google.common.base.Preconditions.checkState;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.collect.KiwiLists.first;

import lombok.experimental.UtilityClass;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;

import java.util.List;

/**
 * Utility methods for finding a desired port out of a list of port definitions,
 * or finding application or admin ports.
 */
@UtilityClass
public class Ports {

    /**
     * Find the single application port. If there are none or more than one, throw an exception.
     *
     * @param ports the ports to filter
     * @return the application port
     * @throws IllegalStateException if there are none or there is more than one port
     */
    public static Port findOnlyApplicationPort(List<Port> ports) {
        var applicationPorts = findApplicationPorts(ports);
        checkExactlyOnePort(ports, "application");
        return first(applicationPorts);
    }

    /**
     * Find only the application ports in the list of ports.
     *
     * @param ports the ports to filter
     * @return a list of application ports
     */
    public static List<Port> findApplicationPorts(List<Port> ports) {
        return findPorts(ports, PortType.APPLICATION);
    }

    /**
     * Find the single admin port. If there are none or more than one, throw an exception.
     *
     * @param ports the ports to filter
     * @return the admin port
     * @throws IllegalStateException if there are none or there is more than one port
     */
    public static Port findOnlyAdminPort(List<Port> ports) {
        var adminPorts = findAdminPorts(ports);
        checkExactlyOnePort(ports, "admin");
        return first(adminPorts);
    }

    private static void checkExactlyOnePort(List<Port> ports, String portType) {
        int numPorts = ports.size();
        checkState(numPorts == 1, "expected one %s port but found %s", portType, numPorts);
    }

    /**
     * Find only the admin ports in the list of ports.
     *
     * @param ports the ports to filter
     * @return a list of application ports
     */
    public static List<Port> findAdminPorts(List<Port> ports) {
        return findPorts(ports, PortType.ADMIN);
    }

    /**
     * Find all ports having the specified {@link PortType}.
     *
     * @param ports the ports to filter
     * @param portType the type of port to find
     * @return a list of ports having the specified type
     */
    public static List<Port> findPorts(List<Port> ports, PortType portType) {
        checkArgumentNotNull(portType, "portType must not be null");
        return ports.stream()
                .filter(port -> port.getType() == portType)
                .toList();
    }

    /**
     * Finds the first port of a given type (Application or Admin) from the list. If multiple ports are found and at
     * least one is marked secure, that port will be given priority.  If multiple ports are found with the same security
     * level, then the first one is returned.
     *
     * @param ports The list of ports to traverse
     * @param type  The type of port that is being requested
     * @return The port definition that was found based on the given criteria
     */
    public static Port findFirstPortPreferSecure(List<Port> ports, PortType type) {
        var securePort = findPort(ports, type, Security.SECURE);
        if (securePort.getNumber() > 0) {
            return securePort;
        }

        return findPort(ports, type, Security.NOT_SECURE);
    }

    /**
     * Finds a desired port given a security and type criteria.
     * <p>
     * If not found, returns a new Port with number 0 and the given values for {@link Security} and {@link PortType}.
     *
     * @param ports    The list of ports to traverse
     * @param type     The type of port that is desired (Application or Admin)
     * @param security The security of the port that is desired (Secure or Non-Secure)
     * @return The port definition that was found based on the given criteria
     */
    public static Port findPort(List<Port> ports, PortType type, Security security) {
        return ports.stream()
                .filter(p -> p.getType() == type)
                .filter(p -> p.getSecure() == security)
                .findFirst()
                .orElseGet(() -> Port.of(0, type, security));
    }

    /**
     * Determines the HTTP scheme to use for a given type
     *
     * @param ports The list of ports to traverse to determine the scheme
     * @param type  The type of port that is desired (Application or Admin)
     * @return The scheme (https or http) based on the port definitions
     * @see #findFirstPortPreferSecure(List, PortType)
     */
    public static String determineScheme(List<Port> ports, PortType type) {
        var firstPort = findFirstPortPreferSecure(ports, type);
        return firstPort.getSecure().getScheme();
    }
}
