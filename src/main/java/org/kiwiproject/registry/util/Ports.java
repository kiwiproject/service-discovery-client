package org.kiwiproject.registry.util;

import lombok.experimental.UtilityClass;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;

import java.util.List;

/**
 * Utility methods for finding a desired port out of a list of port definitions
 */
@UtilityClass
public class Ports {

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
        var securePort = findPort(ports, Security.SECURE, type);
        if (securePort.getNumber() > 0) {
            return securePort;
        }

        return findPort(ports, Security.NOT_SECURE, type);
    }

    /**
     * Finds a desired port given a security and type criteria.
     * <p>
     * If not found, returns a new Port with number 0 and the given values for {@link Security} and {@link PortType}.
     *
     * @param ports    The list of ports to traverse
     * @param security The security of the port that is desired (Secure or Non-Secure)
     * @param type     The type of port that is desired (Application or Admin)
     * @return The port definition that was found based on the given criteria
     */
    public static Port findPort(List<Port> ports, Security security, PortType type) {
        return ports.stream()
                .filter(p -> p.getSecure() == security)
                .filter(p -> p.getType() == type)
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
