package org.kiwiproject.registry.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

        @ParameterizedTest
        @CsvSource(textBlock = """
                APPLICATION, SECURE, 9090
                APPLICATION, NOT_SECURE, 8080
                ADMIN, SECURE, 9091
                ADMIN, NOT_SECURE, 8081
                """)
        void shouldFindExpectedPort(PortType portType, Security security, int expectedPortNumber) {
            var ports = List.of(
                    newApplicationPort(8080, Security.NOT_SECURE),
                    newAdminPort(8081, Security.NOT_SECURE),
                    newApplicationPort(9090, Security.SECURE),
                    newAdminPort(9091, Security.SECURE)
            );

            var port = Ports.findPort(ports, portType, security);

            assertThat(port.getNumber()).isEqualTo(expectedPortNumber);
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
            var ports = List.of(newApplicationPort(8080), newAdminPort(8081));

            var applicationPort = Ports.findOnlyApplicationPort(ports);
            assertThat(applicationPort.getNumber()).isEqualTo(8080);
        }

        @Test
        void shouldThrowIllegalStateWhenNoApplicationPorts() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> Ports.findOnlyApplicationPort(List.of()))
                    .withMessage("expected one application port but found 0");
        }

        @Test
        void shouldThrowIllegalStateWhenMoreThanOneApplicationPort() {
            var ports = List.of(newApplicationPort(8080), newApplicationPort(9090));
            assertThatIllegalStateException()
                    .isThrownBy(() -> Ports.findOnlyApplicationPort(ports))
                    .withMessage("expected one application port but found 2");
        }
    }

    @Nested
    class FindApplicationPorts {

        @Test
        void shouldFindOnlyApplicationPorts() {
            var ports = List.of(newApplicationPort(8080),
                    newAdminPort(8081),
                    newApplicationPort(8082),
                    newAdminPort(8083));

            var applicationPorts = Ports.findApplicationPorts(ports);
            assertThat(applicationPorts).extracting(Port::getNumber).containsOnly(8080, 8082);
        }

        @Test
        void shouldBeEmptyWhenThereAreNoApplicationPorts() {
            var ports = List.of(newAdminPort(8081));
            assertThat(Ports.findApplicationPorts(ports)).isEmpty();
        }
    }

    @Nested
    class FindOnlyAdminPort {

        @Test
        void shouldGetTheSingleAdminPort() {
            var ports = List.of(newApplicationPort(8080), newAdminPort(8081));

            var adminPort = Ports.findOnlyAdminPort(ports);
            assertThat(adminPort.getNumber()).isEqualTo(8081);
        }

        @Test
        void shouldThrowIllegalStateWhenNoAdminPorts() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> Ports.findOnlyAdminPort(List.of()))
                    .withMessage("expected one admin port but found 0");
        }

        @Test
        void shouldThrowIllegalStateWhenMoreThanOneApplicationPort() {
            var ports = List.of(newAdminPort(8081), newAdminPort(9091));
            assertThatIllegalStateException()
                    .isThrownBy(() -> Ports.findOnlyAdminPort(ports))
                    .withMessage("expected one admin port but found 2");
        }
    }

    @Nested
    class FindAdminPorts {

        @Test
        void shouldFindOnlyAdminPorts() {
            var ports = List.of(newApplicationPort(8080),
                    newAdminPort(8081),
                    newApplicationPort(8082),
                    newAdminPort(8083));

            var adminPorts = Ports.findAdminPorts(ports);
            assertThat(adminPorts).extracting(Port::getNumber).containsOnly(8081, 8083);
        }

        @Test
        void shouldBeEmptyWhenThereAreNoApplicationPorts() {
            var ports = List.of(newApplicationPort(8080));
            assertThat(Ports.findAdminPorts(ports)).isEmpty();
        }
    }

    @Nested
    class FindPortsOfType {

        @Test
        void shouldFindApplicationPorts() {
            var ports = List.of(newApplicationPort(8080),
                    newAdminPort(8081),
                    newApplicationPort(8082),
                    newAdminPort(8083));

            var applicationPorts = Ports.findPorts(ports, PortType.APPLICATION);
            assertThat(applicationPorts).extracting(Port::getNumber).containsOnly(8080, 8082);
        }

        @Test
        void shouldFindAdminPorts() {
            var ports = List.of(newApplicationPort(8080),
                    newAdminPort(8081),
                    newApplicationPort(8082),
                    newAdminPort(8083));

            var adminPorts = Ports.findPorts(ports, PortType.ADMIN);
            assertThat(adminPorts).extracting(Port::getNumber).containsOnly(8081, 8083);
        }
    }

    private static Port newApplicationPort(int number) {
        return newApplicationPort(number, Security.SECURE);
    }

    private static Port newApplicationPort(int number, Security security) {
        return Port.of(number, PortType.APPLICATION, security);
    }

    private static Port newAdminPort(int number) {
        return newAdminPort(number, Security.SECURE);
    }

    private static Port newAdminPort(int number, Security security) {
        return Port.of(number, PortType.ADMIN, security);
    }
}
