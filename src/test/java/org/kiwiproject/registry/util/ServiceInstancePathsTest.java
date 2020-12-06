package org.kiwiproject.registry.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;
import org.kiwiproject.test.junit.jupiter.params.provider.AsciiOnlyBlankStringArgumentsProvider;

import java.util.List;

@DisplayName("ServiceInstancePaths")
class ServiceInstancePathsTest {

    @Nested
    class UrlForPath {

        @Test
        void shouldBuildUrlWithHttpsWhenSecurePortIsFound() {
            var ports = List.of(Port.of(8080, PortType.APPLICATION, Security.SECURE));

            var url = ServiceInstancePaths.urlForPath("localhost", ports, PortType.APPLICATION, "/foo");

            assertThat(url).isEqualTo("https://localhost:8080/foo");
        }

        @Test
        void shouldBuildUrlWithHttpWhenNonSecurePortIsFound() {
            var ports = List.of(Port.of(8080, PortType.APPLICATION, Security.NOT_SECURE));

            var url = ServiceInstancePaths.urlForPath("localhost", ports, PortType.APPLICATION, "/foo");

            assertThat(url).isEqualTo("http://localhost:8080/foo");
        }

        @Test
        void shouldBuildUrlWithHttpsWhenBothSecureAndNonSecurePortIsFound() {
            var ports = List.of(
                    Port.of(8080, PortType.APPLICATION, Security.SECURE),
                    Port.of(8081, PortType.APPLICATION, Security.NOT_SECURE)
            );

            var url = ServiceInstancePaths.urlForPath("localhost", ports, PortType.APPLICATION, "/foo");

            assertThat(url).isEqualTo("https://localhost:8080/foo");
        }

        @Nested
        class GivenUnexpectedPaths {

            private List<Port> ports;

            @BeforeEach
            void setUp() {
                ports = List.of(Port.of(8080, PortType.APPLICATION, Security.SECURE));
            }

            @ParameterizedTest
            @ArgumentsSource(AsciiOnlyBlankStringArgumentsProvider.class)
            void shouldIgnoreBlankPaths(String path) {
                var url = ServiceInstancePaths.urlForPath("localhost", ports, PortType.APPLICATION, path);

                assertThat(url).isEqualTo("https://localhost:8080");
            }

            @ParameterizedTest
            @ValueSource(strings = {"/", "\t/\n", "  /  "})
            void shouldRetainSlashOnlyPaths(String path) {
                var url = ServiceInstancePaths.urlForPath("localhost", ports, PortType.APPLICATION, path);

                assertThat(url).isEqualTo("https://localhost:8080/");
            }

            @ParameterizedTest
            @ValueSource(strings = {"foo", "bar", "baz/42", "  foo  ", "  foo/84  ", "\t\tbar/baz/42\n\n"})
            void shouldPrependPathWithSlash(String path) {
                var url = ServiceInstancePaths.urlForPath("localhost", ports, PortType.APPLICATION, path);

                assertThat(url).isEqualTo("https://localhost:8080/" + path.trim());
            }
        }
    }
}
