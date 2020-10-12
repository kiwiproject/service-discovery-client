package org.kiwiproject.registry.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Model that defines a port being used by a service, including the port number, the purpose of the port, and whether the port is secure or not.
 * <p>
 * For the type/purpose of the port, we are assuming that a service has a separate ports for the main application and the administrative endpoints
 * (e.g. status and health checks)
 */
@Builder
@Getter
public class Port {

    /**
     * Enum defining the type of connector the port is used for.
     */
    public enum PortType {
        APPLICATION, ADMIN
    }

    /**
     * Enum defining whether the port is secure or not
     */
    public enum Security {
        SECURE("https"), NOT_SECURE("http");

        @Getter
        private final String scheme;

        Security(String scheme) {
            this.scheme = scheme;
        }

        public static Security fromScheme(String schemeToCheck) {
            return SECURE.scheme.equalsIgnoreCase(schemeToCheck) ? SECURE : NOT_SECURE;
        }
    }

    private final int number;
    private final PortType type;
    private final Security secure;

}
