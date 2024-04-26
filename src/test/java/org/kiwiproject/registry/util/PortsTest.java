package org.kiwiproject.registry.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;

import java.util.List;

@DisplayName("Ports")
class PortsTest {

    @Nested
    class FindFirstPortPreferSecure {

        @Test
        void shouldReturnSecurePortWhenSecurePortIsFound() {
            var ports = List.of(Port.of(8080, PortType.APPLICATION, Security.SECURE));

            var port = Ports.findFirstPortPreferSecure(ports, PortType.APPLICATION);

            assertThat(port.getNumber()).isEqualTo(8080);
            assertThat(port.getSecure()).isEqualTo(Security.SECURE);
            assertThat(port.getType()).isEqualTo(PortType.APPLICATION);
        }

        @Test
        void shouldReturnNonSecureWhenNonSecurePortIsFound() {
            var ports = List.of(Port.of(8080, PortType.APPLICATION, Security.NOT_SECURE));

            var port = Ports.findFirstPortPreferSecure(ports, PortType.APPLICATION);

            assertThat(port.getNumber()).isEqualTo(8080);
            assertThat(port.getSecure()).isEqualTo(Security.NOT_SECURE);
            assertThat(port.getType()).isEqualTo(PortType.APPLICATION);
        }

        @Test
        void shouldReturnSecureWhenBothSecureAndNonSecurePortIsFound() {
            var ports = List.of(
                    Port.of(8080, PortType.APPLICATION, Security.SECURE),
                    Port.of(8081, PortType.APPLICATION, Security.NOT_SECURE)
            );

            var port = Ports.findFirstPortPreferSecure(ports, PortType.APPLICATION);

            assertThat(port.getNumber()).isEqualTo(8080);
            assertThat(port.getSecure()).isEqualTo(Security.SECURE);
            assertThat(port.getType()).isEqualTo(PortType.APPLICATION);
        }

        @Test
        void shouldReturnFirstSecureWhenMultiplePortsAreFound() {
            var ports = List.of(
                    Port.of(8080, PortType.APPLICATION, Security.SECURE),
                    Port.of(8081, PortType.APPLICATION, Security.SECURE)
            );

            var port = Ports.findFirstPortPreferSecure(ports, PortType.APPLICATION);

            assertThat(port.getNumber()).isEqualTo(8080);
            assertThat(port.getSecure()).isEqualTo(Security.SECURE);
            assertThat(port.getType()).isEqualTo(PortType.APPLICATION);
        }
    }

    @Nested
    class FindPort {

        @Test
        void shouldReturnDefaultPortIfCriteriaDoesNotFindOne() {
            var port = Ports.findPort(List.of(), PortType.APPLICATION, Security.SECURE);

            assertThat(port.getNumber()).isZero();
            assertThat(port.getSecure()).isEqualTo(Security.SECURE);
            assertThat(port.getType()).isEqualTo(PortType.APPLICATION);
        }
    }

    @Nested
    class DetermineScheme {

        @Test
        void shouldReturnHttpsIfPortIsSecure() {
            var ports = List.of(Port.of(8080, PortType.APPLICATION, Security.SECURE));

            var scheme = Ports.determineScheme(ports, PortType.APPLICATION);

            assertThat(scheme).isEqualTo("https");
        }

        @Test
        void shouldReturnHttpIfPortIsNotSecure() {
            var ports = List.of(Port.of(8080, PortType.APPLICATION, Security.NOT_SECURE));

            var scheme = Ports.determineScheme(ports, PortType.APPLICATION);

            assertThat(scheme).isEqualTo("http");
        }
    }

    @Nested
    class FindOnlyApplicationPort {

        @Test
        void shouldGetTheSingleApplicationPort() {
            // TODO
        }

        @Test
        void shouldThrowIllegalStateWhenNoApplicationPorts() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> Ports.findOnlyApplicationPort(List.of()))
                    .withMessage("expected one application port but found 0");
        }

        @Test
        void shouldThrowIllegalStateWhenMoreThanOneApplicationPort() {
            // TODO
        }
    }

    @Nested
    class FindApplicationPorts {

        @Test
        void shouldFindOnlyApplicationPorts() {
            // TODO
        }
    }

    @Nested
    class FindOnlyAdminPort {

        @Test
        void shouldGetTheSingleApplicationPort() {
            // TODO
        }

        @Test
        void shouldThrowIllegalStateWhenNoAdminPorts() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> Ports.findOnlyAdminPort(List.of()))
                    .withMessage("expected one admin port but found 0");
        }

        @Test
        void shouldThrowIllegalStateWhenMoreThanOneApplicationPort() {
            // TODO
        }
    }

    @Nested
    class FindAdminPorts {

        @Test
        void shouldFindOnlyAdminPorts() {
            // TODO
        }
    }

    @Nested
    class FindPortsOfType {

        @Test
        void shouldFindApplicationPorts() {
            // TODO
        }

        @Test
        void shouldFindAdminPorts() {
            // TODO
        }
    }

    private static Port newApplicationPort(int number) {
        return Port.of(number, PortType.APPLICATION, Security.SECURE);
    }

    private static Port newAdminPort(int number) {
        return Port.of(number, PortType.ADMIN, Security.SECURE);
    }
}
