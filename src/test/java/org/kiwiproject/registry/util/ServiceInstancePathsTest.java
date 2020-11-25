package org.kiwiproject.registry.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;

import java.util.List;

@DisplayName("Paths")
class ServiceInstancePathsTest {

    @Nested
    class UrlForPath {

        @Test
        void shouldBuildPathWithHttpsWhenSecurePortIsFound() {
            var ports = List.of(Port.of(8080, PortType.APPLICATION, Security.SECURE));

            var path = ServiceInstancePaths.urlForPath("localhost", ports, PortType.APPLICATION, "/foo");

            assertThat(path).isEqualTo("https://localhost:8080/foo");
        }

        @Test
        void shouldBuildPathWithHttpWhenNonSecurePortIsFound() {
            var ports = List.of(Port.of(8080, PortType.APPLICATION, Security.NOT_SECURE));

            var path = ServiceInstancePaths.urlForPath("localhost", ports, PortType.APPLICATION, "/foo");

            assertThat(path).isEqualTo("http://localhost:8080/foo");
        }

        @Test
        void shouldBuildPathWithHttpsWhenBothSecureAndNonSecurePortIsFound() {
            var ports = List.of(
                    Port.of(8080, PortType.APPLICATION, Security.SECURE),
                    Port.of(8081, PortType.APPLICATION, Security.NOT_SECURE)
            );

            var path = ServiceInstancePaths.urlForPath("localhost", ports, PortType.APPLICATION, "/foo");

            assertThat(path).isEqualTo("https://localhost:8080/foo");
        }
    }
}
