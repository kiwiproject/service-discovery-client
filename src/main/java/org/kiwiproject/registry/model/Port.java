package org.kiwiproject.registry.model;

import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiPreconditions.checkValidPort;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import javax.annotation.Nullable;

/**
 * Model that defines a port being used by a service, including the port number, the purpose of the port, and whether
 * the port is secure or not.
 * <p>
 * For the type/purpose of the port, we are assuming that a service has a separate ports for the main application and
 * the administrative endpoints (e.g. status and health checks)
 */
@Builder
@Getter
@ToString
public class Port {

    /**
     * Enum defining the type of connector the port is used for.
     */
    public enum PortType {
        APPLICATION, ADMIN
    }

    /**
     * Enum defining whether the port is secure or not.
     */
    public enum Security {

        /**
         * Secure; HTTPS.
         */
        SECURE("https"),

        /**
         * Not secure; HTTP.
         */
        NOT_SECURE("http");

        @Getter
        private final String scheme;

        Security(String scheme) {
            this.scheme = scheme;
        }

        /**
         * Return {@link Security} value for the given scheme, using a case-insensitive comparison. If given some
         * value other than HTTP or HTTPS, returns SECURE.
         *
         * @param schemeToCheck either HTTP or HTTPS, case-insensitive
         * @return the {@link Security} value
         */
        public static Security fromScheme(String schemeToCheck) {
            return NOT_SECURE.scheme.equalsIgnoreCase(schemeToCheck) ? NOT_SECURE : SECURE;
        }
    }

    private final int number;
    private final PortType type;
    private final Security secure;

    /**
     * Convenience factory method to create a new {@link Port}.
     * <p>
     * Default values are assigned to the port type and security if null arguments are supplied.
     *
     * @param number   the port number, must be in range 0 to 65535
     * @param portType the type of port (defaults to APPLICATION if null)
     * @param security is the port secure? (defaults to SECURE if null)
     * @return a new instance
     */
    public static Port of(int number, @Nullable PortType portType, @Nullable Security security) {
        checkValidPort(number);

        var nonNullPortType = isNull(portType) ? PortType.APPLICATION : portType;
        var nonNullSecurity = isNull(security) ? Security.SECURE : security;

        return Port.builder()
                .number(number)
                .type(nonNullPortType)
                .secure(nonNullSecurity)
                .build();
    }

    /**
     * Is this port secure?
     *
     * @return true if this port is {@link Security#SECURE}
     */
    public boolean isSecure() {
        return secure == Security.SECURE;
    }

    /**
     * Return the scheme used by the {@link Security} of this port.
     *
     * @return the scheme (e.g. "https") for connecting to this port
     */
    public String getScheme() {
        return secure.getScheme();
    }

    /**
     * Is this an application port?
     *
     * @return true if port type is {@link PortType#APPLICATION}
     */
    public boolean isApplication() {
        return type == PortType.APPLICATION;
    }

    /**
     * Is this an admin port?
     *
     * @return true if port type is {@link PortType#ADMIN}
     */
    public boolean isAdmin() {
        return type == PortType.ADMIN;
    }
}
