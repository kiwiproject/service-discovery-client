package org.kiwiproject.registry.eureka.client;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.collect.KiwiMaps.newHashMap;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.registry.eureka.common.EurekaInstance;
import org.kiwiproject.test.util.Fixtures;

import java.util.List;
import java.util.Map;

@DisplayName("EurekaParser")
@ExtendWith(SoftAssertionsExtension.class)
class EurekaParserTest {

    @Test
    void parseEurekaResponse_ShouldThrowIllegalArgumentException_WhenResponseMapIsNull() {
        assertThatThrownBy(() -> EurekaParser.parseEurekaResponse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Map data of Eureka response must not be null");
    }

    @Test
    void parseEurekaResponse_ShouldThrowIllegalStateException_WhenResponseMapIsMissingApplicationsElement() {
        assertThatThrownBy(() -> EurekaParser.parseEurekaResponse(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Eureka data must contain a key 'applications' that contains a Map");
    }

    @Test
    void parseEurekaResponse_ShouldThrowIllegalArgumentException_WhenApplicationListIsMissingInstances() {
        Map<String, Object> data = Map.of(
                "applications", Map.of(
                        "application", List.of(newHashMap("instance", null))
                )
        );

        assertThatThrownBy(() -> EurekaParser.parseEurekaResponse(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Instance data from Eureka can not be null");
    }

    @Test
    void parseEurekaResponse_ShouldThrowIllegalArgumentException_WhenApplicationMapIsMissingInstances() {
        Map<String, Object> data = Map.of(
                "applications", Map.of(
                        "application", newHashMap("instance", null)
                )
        );

        assertThatThrownBy(() -> EurekaParser.parseEurekaResponse(data))
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
    void parseEurekaResponseShouldParseSuccessfully(String sampleJsonFile, SoftAssertions softly) {
        var sampleData = JSON_HELPER.toMap(Fixtures.fixture(sampleJsonFile));

        var parsedDataList = EurekaParser.parseEurekaResponse(sampleData);

        assertThat(parsedDataList).hasSize(1);

        var parsedData = parsedDataList.get(0);

        assertEurekaInstance(parsedData, softly);
    }

    @SuppressWarnings("unchecked")
    private void assertEurekaInstance(EurekaInstance instance, SoftAssertions softly) {
        softly.assertThat(instance.getInstanceId()).isEqualTo("localhost");
        softly.assertThat(instance.getApp()).isEqualTo("TEST-SERVICE");
        softly.assertThat(instance.getVipAddress()).isEqualTo("TEST-SERVICE-LOCALHOST");
        softly.assertThat(instance.getIpAddr()).isEqualTo("127.0.0.1");
        softly.assertThat(instance.getStatus()).isEqualTo("UP");
        softly.assertThat(instance.getHomePageUrl()).isEqualTo("http://localhost");
        softly.assertThat(instance.getHealthCheckUrl()).isEqualTo("http://localhost/healthCheck");
        softly.assertThat(instance.getStatusPageUrl()).isEqualTo("http://localhost/ping");

        softly.assertThat(instance.getPort()).contains(
                entry("$", 8080),
                entry("@enabled", true)
        );

        softly.assertThat(instance.getSecurePort()).contains(
                entry("$", 0),
                entry("@enabled", false)
        );

        softly.assertThat(instance.getMetadata()).contains(
                entry("description", "A simple service"),
                entry("commitRef", "abcdef"),
                entry("version", "2018.01.01")
        );

        softly.assertThat(instance.getLeaseInfo()).contains(
                entry("durationInSecs", 5)
        );
    }

    @Test
    void parseEurekaResponseShouldParseSuccessfully_EvenIfLeaseInfoIsMissing() {
        Map<String, Object> data = Map.of(
                "applications", Map.of(
                        "application", newHashMap("instance", Map.of())
                )
        );

        var dataList = EurekaParser.parseEurekaResponse(data);

        assertThat(dataList.get(0).getLeaseInfo()).isNotNull().isEmpty();
    }
}
