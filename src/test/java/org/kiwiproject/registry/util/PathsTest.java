package org.kiwiproject.registry.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.model.Port;

import java.util.List;

@DisplayName("Paths")
class PathsTest {

    @Nested
    class UrlForPath {

        @Test
        void shouldBuildPathWithHttpsWhenSecurePortIsFound() {
            var ports = List.of(Port.builder().number(8080).type(Port.PortType.APPLICATION).secure(Port.Security.SECURE).build());

            var path = Paths.urlForPath("localhost", ports, Port.PortType.APPLICATION, "/foo");

            assertThat(path).isEqualTo("https://localhost:8080/foo");
        }

        @Test
        void shouldBuildPathWithHttpWhenNonSecurePortIsFound() {
            var ports = List.of(Port.builder().number(8080).type(Port.PortType.APPLICATION).secure(Port.Security.NOT_SECURE).build());

            var path = Paths.urlForPath("localhost", ports, Port.PortType.APPLICATION, "/foo");

            assertThat(path).isEqualTo("http://localhost:8080/foo");
        }

        @Test
        void shouldBuildPathWithHttpsWhenBothSecureAndNonSecurePortIsFound() {
            var ports = List.of(
                    Port.builder().number(8080).type(Port.PortType.APPLICATION).secure(Port.Security.SECURE).build(),
                    Port.builder().number(8081).type(Port.PortType.APPLICATION).secure(Port.Security.NOT_SECURE).build()
            );

            var path = Paths.urlForPath("localhost", ports, Port.PortType.APPLICATION, "/foo");

            assertThat(path).isEqualTo("https://localhost:8080/foo");
        }
    }
}
