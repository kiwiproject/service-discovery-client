package org.kiwiproject.registry.util;

import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.registry.util.Ports.findFirstPortPreferSecure;

import lombok.experimental.UtilityClass;
import org.kiwiproject.registry.model.Port;

import java.util.List;

/**
 * Utility methods for generating URLs from the service information
 */
@UtilityClass
public class ServiceInstancePaths {

    private static final String URL_HOST_FORMAT = "{}://{}:{}{}";

    /**
     * Generates a URL given service information
     *
     * @param hostName  The hostname for the url
     * @param ports     The list of possible ports to use
     * @param type      The type of port (Application versus Admin) for the url
     * @param path      The path to append to the url
     * @return  The combined URL
     */
    public static String urlForPath(String hostName, List<Port> ports, Port.PortType type, String path) {
        var port = findFirstPortPreferSecure(ports, type);

        var protocol = port.getSecure().getScheme();
        return f(URL_HOST_FORMAT, protocol, hostName, port.getNumber(), path);
    }

}
