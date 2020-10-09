package org.kiwiproject.registry.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.model.Port;

import java.util.List;

@DisplayName("Ports")
class PortsTest {

    @Nested
    class FindFirstPortPreferSecure {

        @Test
        void shouldReturnSecurePortWhenSecurePortIsFound() {
            var ports = List.of(Port.builder().number(8080).type(Port.PortType.APPLICATION).secure(Port.Security.SECURE).build());

            var port = Ports.findFirstPortPreferSecure(ports, Port.PortType.APPLICATION);

            assertThat(port.getNumber()).isEqualTo(8080);
            assertThat(port.getSecure()).isEqualTo(Port.Security.SECURE);
            assertThat(port.getType()).isEqualTo(Port.PortType.APPLICATION);
        }

        @Test
        void shouldReturnNonSecureWhenNonSecurePortIsFound() {
            var ports = List.of(Port.builder().number(8080).type(Port.PortType.APPLICATION).secure(Port.Security.NOT_SECURE).build());

            var port = Ports.findFirstPortPreferSecure(ports, Port.PortType.APPLICATION);

            assertThat(port.getNumber()).isEqualTo(8080);
            assertThat(port.getSecure()).isEqualTo(Port.Security.NOT_SECURE);
            assertThat(port.getType()).isEqualTo(Port.PortType.APPLICATION);
        }

        @Test
        void shouldReturnSecureWhenBothSecureAndNonSecurePortIsFound() {
            var ports = List.of(
                    Port.builder().number(8080).type(Port.PortType.APPLICATION).secure(Port.Security.SECURE).build(),
                    Port.builder().number(8081).type(Port.PortType.APPLICATION).secure(Port.Security.NOT_SECURE).build()
            );

            var port = Ports.findFirstPortPreferSecure(ports, Port.PortType.APPLICATION);

            assertThat(port.getNumber()).isEqualTo(8080);
            assertThat(port.getSecure()).isEqualTo(Port.Security.SECURE);
            assertThat(port.getType()).isEqualTo(Port.PortType.APPLICATION);
        }

        @Test
        void shouldReturnFirstSecureWhenMultiplePortsAreFound() {
            var ports = List.of(
                    Port.builder().number(8080).type(Port.PortType.APPLICATION).secure(Port.Security.SECURE).build(),
                    Port.builder().number(8081).type(Port.PortType.APPLICATION).secure(Port.Security.SECURE).build()
            );

            var port = Ports.findFirstPortPreferSecure(ports, Port.PortType.APPLICATION);

            assertThat(port.getNumber()).isEqualTo(8080);
            assertThat(port.getSecure()).isEqualTo(Port.Security.SECURE);
            assertThat(port.getType()).isEqualTo(Port.PortType.APPLICATION);
        }
    }

    @Nested
    class FindPort {

        @Test
        void shouldReturnDefaultPortIfCriteriaDoesNotFindOne() {
            var port = Ports.findPort(List.of(), Port.Security.SECURE, Port.PortType.APPLICATION);

            assertThat(port.getNumber()).isZero();
            assertThat(port.getSecure()).isEqualTo(Port.Security.SECURE);
            assertThat(port.getType()).isEqualTo(Port.PortType.APPLICATION);
        }
    }

    @Nested
    class DetermineScheme {

        @Test
        void shouldReturnHttpsIfPortIsSecure() {
            var ports = List.of(Port.builder().number(8080).type(Port.PortType.APPLICATION).secure(Port.Security.SECURE).build());

            var scheme = Ports.determineScheme(ports, Port.PortType.APPLICATION);

            assertThat(scheme).isEqualTo("https");
        }

        @Test
        void shouldReturnHttpIfPortIsNotSecure() {
            var ports = List.of(Port.builder().number(8080).type(Port.PortType.APPLICATION).secure(Port.Security.NOT_SECURE).build());

            var scheme = Ports.determineScheme(ports, Port.PortType.APPLICATION);

            assertThat(scheme).isEqualTo("http");
        }
    }
}
