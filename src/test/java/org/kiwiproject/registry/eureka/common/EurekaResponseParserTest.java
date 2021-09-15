package org.kiwiproject.registry.eureka.common;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.collect.KiwiMaps.newHashMap;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.test.util.Fixtures;

import java.util.List;
import java.util.Map;

@DisplayName("EurekaResponseParser")
@ExtendWith(SoftAssertionsExtension.class)
class EurekaResponseParserTest {

    @Nested
    class ParseEurekaApplicationsResponse {

        @Test
        void shouldThrowIllegalArgumentException_WhenResponseMapIsNull() {
            assertThatThrownBy(() -> EurekaResponseParser.parseEurekaApplicationsResponse(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Eureka applications response must not be null");
        }

        @Test
        void shouldThrowIllegalStateException_WhenResponseMapIsMissingApplicationsElement() {
            assertThatThrownBy(() -> EurekaResponseParser.parseEurekaApplicationsResponse(Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Eureka data must contain a key 'applications' that contains a Map<String, Object>");
        }

        @Test
        void shouldThrowIllegalArgumentException_WhenApplicationListIsMissingInstances() {
            Map<String, Object> data = Map.of(
                    "applications", Map.of(
                            "application", List.of(newHashMap("instance", null))
                    )
            );

            assertThatThrownBy(() -> EurekaResponseParser.parseEurekaApplicationsResponse(data))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Instance data from Eureka can not be null");
        }

        @Test
        void shouldThrowIllegalArgumentException_WhenApplicationMapIsMissingInstances() {
            Map<String, Object> data = Map.of(
                    "applications", Map.of(
                            "application", newHashMap("instance", null)
                    )
            );

            assertThatThrownBy(() -> EurekaResponseParser.parseEurekaApplicationsResponse(data))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Instance data from Eureka can not be null");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "EurekaParserTest/eureka-response-as-all-lists.json",
                "EurekaParserTest/eureka-response-as-application-list-instance-map.json",
                "EurekaParserTest/eureka-response-as-application-map-instance-list.json",
                "EurekaParserTest/eureka-response-as-all-maps.json"
        })
        void shouldParseSuccessfully(String sampleJsonFile, SoftAssertions softly) {
            var eurekaResponse = JSON_HELPER.toMap(Fixtures.fixture(sampleJsonFile));

            var eurekaInstanceList = EurekaResponseParser.parseEurekaApplicationsResponse(eurekaResponse);

            assertThat(eurekaInstanceList).hasSize(1);

            var instance = first(eurekaInstanceList);

            assertEurekaInstance(instance, softly);
        }

        @Test
        void shouldParseSuccessfully_EvenIfLeaseInfoIsMissing() {
            Map<String, Object> data = Map.of(
                    "applications", Map.of(
                            "application", newHashMap("instance", Map.of())
                    )
            );

            var eurekaInstanceList = EurekaResponseParser.parseEurekaApplicationsResponse(data);

            assertThat(first(eurekaInstanceList).getLeaseInfo()).isEmpty();
        }
    }

    @Nested
    class ParseEurekaInstanceResponse {

        @Test
        void shouldThrowIllegalArgumentException_WhenResponseMapIsNull() {
            assertThatThrownBy(() -> EurekaResponseParser.parseEurekaInstanceResponse(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Eureka instance response must not be null");
        }

        @Test
        void shouldThrowIllegalStateException_WhenResponseMapIsMissingInstanceElement() {
            assertThatThrownBy(() -> EurekaResponseParser.parseEurekaInstanceResponse(Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Eureka data must contain a key 'instance' that contains a Map<String, Object>");
        }

        @Test
        void shouldSuccessfullyParseValidResponse(SoftAssertions softly) {
            var eurekaResponse = JSON_HELPER.toMap(Fixtures.fixture("EurekaParserTest/eureka-instance-response.json"));

            var eurekaInstance = EurekaResponseParser.parseEurekaInstanceResponse(eurekaResponse);

            assertEurekaInstance(eurekaInstance, softly);
        }
    }

    private void assertEurekaInstance(EurekaInstance instance, SoftAssertions softly) {
        softly.assertThat(instance.getInstanceId()).isEqualTo("localhost");
        softly.assertThat(instance.getApp()).isEqualTo("TEST-SERVICE");
        softly.assertThat(instance.getVipAddress()).isEqualTo("TEST-SERVICE-LOCALHOST");
        softly.assertThat(instance.getIpAddr()).isEqualTo("127.0.0.1");
        softly.assertThat(instance.getStatus()).isEqualTo("UP");
        softly.assertThat(instance.getHomePageUrl()).isEqualTo("http://localhost");
        softly.assertThat(instance.getHealthCheckUrl()).isEqualTo("http://localhost/healthcheck");
        softly.assertThat(instance.getStatusPageUrl()).isEqualTo("http://localhost/ping");

        softly.assertThat(instance.getPort()).contains(
                entry("$", 8080),
                entry("@enabled", true)
        );

        softly.assertThat(instance.getSecurePort()).contains(
                entry("$", 0),
                entry("@enabled", false)
        );

        softly.assertThat(instance.getAdminPort()).isEqualTo(80);

        softly.assertThat(instance.getMetadata()).contains(
                entry("description", "A simple service"),
                entry("commitRef", "abcdef"),
                entry("version", "2018.01.01")
        );

        softly.assertThat(instance.getLeaseInfo()).contains(
                entry("durationInSecs", 5)
        );
    }

    @Nested
    class GetAdminPort {

        @ParameterizedTest
        @NullAndEmptySource
        void shouldReturnZero_GivenEmptyOrNull(String url) {
            assertThat(EurekaResponseParser.getAdminPort(url)).isZero();
        }

        @ParameterizedTest
        @CsvSource({
                "http://localhost:8081/ping, 8081",
                "http://localhost:9042/healthcheck, 9042",
                "https://localhost:45321/healthcheck, 45321",
                "https://localhost:12345/ping, 12345",
                "https://localhost:65535/healthcheck, 65535",
        })
        void shouldReturnExplicitPorts(String url, int expectedAdminPort) {
            assertThat(EurekaResponseParser.getAdminPort(url)).isEqualTo(expectedAdminPort);
        }

        @ParameterizedTest
        @CsvSource({
                "http://localhost/ping, 80",
                "http://localhost/healthcheck, 80",
                "https://localhost/ping, 443",
                "https://localhost/healthcheck, 443",
        })
        void shouldReturnDefaultHttpPorts(String url, int expectedAdminPort) {
            assertThat(EurekaResponseParser.getAdminPort(url)).isEqualTo(expectedAdminPort);
        }
    }
}
