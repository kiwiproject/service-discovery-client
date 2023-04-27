package org.kiwiproject.registry.util;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.net.KiwiUrls.prependLeadingSlash;
import static org.kiwiproject.registry.util.Ports.findFirstPortPreferSecure;

import lombok.experimental.UtilityClass;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;

import java.util.List;

/**
 * Utility methods for generating URLs from the service information
 */
@UtilityClass
public class ServiceInstancePaths {

    // scheme://hostName:port/path
    private static final String URL_HOST_FORMAT = "{}://{}:{}{}";

    /**
     * Generates a URL given service information.
     *
     * @param hostName The hostname for the url
     * @param ports    The list of possible ports to use
     * @param type     The type of port (Application versus Admin) for the url
     * @param path     The path to append to the url, with or without a leading "/". May be {@code null}
     * @return The combined URL
     */
    public static String urlForPath(String hostName, List<Port> ports, PortType type, @Nullable String path) {
        var port = findFirstPortPreferSecure(ports, type);

        var protocol = port.getSecure().getScheme();

        var safePath = isBlank(path) ? "" : prependLeadingSlash(path);
        return f(URL_HOST_FORMAT, protocol, hostName, port.getNumber(), safePath);
    }

}
