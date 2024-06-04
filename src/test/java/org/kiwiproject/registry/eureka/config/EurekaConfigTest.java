package org.kiwiproject.registry.eureka.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.collect.KiwiLists.fourth;
import static org.kiwiproject.collect.KiwiLists.second;
import static org.kiwiproject.collect.KiwiLists.third;

import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.kiwiproject.json.JsonHelper;
import org.kiwiproject.test.util.Fixtures;
import org.kiwiproject.yaml.YamlHelper;
import org.slf4j.event.Level;

import java.util.List;

@DisplayName("EurekaConfig")
class EurekaConfigTest {

    @RepeatedTest(5)
    void shouldGenerateUniqueRetryIdsIncreasingByOne() {
        var retryIds = List.of(
                retryIdOfNewEurekaConfig(),
                retryIdOfNewEurekaConfig(),
                retryIdOfNewEurekaConfig(),
                retryIdOfNewEurekaConfig()
        );

        var uniqueParts = retryIds.stream()
                .map(EurekaConfigTest::extractRetryIdUniquePart)
                .toList();

        var firstId = first(uniqueParts);
        assertThat(second(uniqueParts)).isEqualTo(firstId + 1);
        assertThat(third(uniqueParts)).isEqualTo(firstId + 2);
        assertThat(fourth(uniqueParts)).isEqualTo(firstId + 3);
    }

    private static String retryIdOfNewEurekaConfig() {
        return new EurekaConfig().getRetryId();
    }

    private static int extractRetryIdUniquePart(String retryId) {
        return Integer.parseInt(retryId.split("-")[1]);
    }

    @Test
    void shouldDefaultRetryProcessingLogLevelToDEBUG() {
        var config = new EurekaConfig();
        assertThat(config.getRetryProcessingLogLevel()).isEqualTo(Level.DEBUG);
    }

    @Test
    void shouldDefaultRetryExceptionLogLevelToWARN() {
        var config = new EurekaConfig();
        assertThat(config.getRetryExceptionLogLevel()).isEqualTo(Level.WARN);
    }

    @Nested
    class SetRetryId {

        @Test
        void shouldPrefixTheGivenIdentifier() {
            var config = new EurekaConfig();
            config.setRetryId("vpc-xyz");
            assertThat(config.getRetryId()).isEqualTo("EurekaRegistryClient-vpc-xyz");
        }

        @Test
        void shouldNotDuplicatePrefix() {
            var config = new EurekaConfig();
            config.setRetryId("EurekaRegistryClient-vpc-test");
            assertThat(config.getRetryId()).isEqualTo("EurekaRegistryClient-vpc-test");
        }
    }

    @Nested
    class GetRegistryUrls {

        @Test
        void shouldReturnOriginalUrlsIfDomainIsNotOverridden() {
            var config = new EurekaConfig();
            config.setRegistryUrls("http://eureka.test:8761/eureka,http://eureka2.test:8761/eureka");

            assertThat(config.getRegistryUrls())
                    .isEqualTo("http://eureka.test:8761/eureka,http://eureka2.test:8761/eureka");
        }

        @Test
        void shouldReturnUrlsWithTheDomainChanged_WhenDomainIsOverridden() {
            var config = new EurekaConfig();
            config.setRegistryUrls("http://eureka.test:8761/eureka,http://eureka2.test:8761/eureka");
            config.setDomainOverride("prod");

            assertThat(config.getRegistryUrls())
                    .isEqualTo("http://eureka.prod:8761/eureka,http://eureka2.prod:8761/eureka");
        }
    }

    @Test
    void shouldDefaultDomainOverrideToNull() {
        var config = new EurekaConfig();

        assertThat(config.getDomainOverride()).isNull();
    }

    @Test
    void shouldDefaultIncludeNativeDataToFalse() {
        var config = new EurekaConfig();
        assertThat(config.isIncludeNativeData()).isFalse();
    }

    @Nested
    class SetRegistryUrls {

        // Tests degenerate condition; when validated registryUrls will fail the @NotBlank validation
        @Test
        void shouldSetFromEmptyList() {
            var config = new EurekaConfig();
            config.setRegistryUrls(List.of());

            assertThat(config.getRegistryUrls()).isEmpty();
        }

        @Test
        void shouldSetFromSingleElementList() {
            var config = new EurekaConfig();
            config.setRegistryUrls(List.of("https://eureka-001.test:8761"));

            assertThat(config.getRegistryUrls()).isEqualTo("https://eureka-001.test:8761");
        }

        @Test
        void shouldSetFromList() {
            var config = new EurekaConfig();
            config.setRegistryUrls(List.of(
                    "https://eureka-1.test:8761",
                    "https://eureka-2.test:8761"
            ));

            assertThat(config.getRegistryUrls())
                    .isEqualTo("https://eureka-1.test:8761,https://eureka-2.test:8761");
        }

        @Nested
        class FromYaml {

            @Test
            void shouldSetFromCsv() {
                var yaml = Fixtures.fixture("EurekaConfigTest/sample-config-csv-urls.yml");
                parseYamlAndAssertRegistryUrls(yaml);
            }

            @Test
            void shouldSetFromList() {
                var yaml = Fixtures.fixture("EurekaConfigTest/sample-config-list-of-urls.yml");
                parseYamlAndAssertRegistryUrls(yaml);
            }

            private void parseYamlAndAssertRegistryUrls(String yaml) {
                var sampleConfig = parseYaml(yaml);
                assertParsedRegistryUrls(sampleConfig);
            }

            @Test
            void shouldSetFromSingleElementList() {
                var yaml = Fixtures.fixture("EurekaConfigTest/sample-config-list-of-one-url.yml");
                var sampleConfig = parseYaml(yaml);
                assertThat(sampleConfig.getEurekaConfig().getRegistryUrls())
                        .isEqualTo("https://eureka-1.acme.com:8761");
            }

            private SampleConfig parseYaml(String yaml) {
                return new YamlHelper().toObject(yaml, SampleConfig.class);
            }
        }

        @Nested
        class FromJson {

            @Test
            void shouldSetFromCsv() {
                var json = Fixtures.fixture("EurekaConfigTest/sample-config-csv-urls.json");
                parseJsonAndAssertRegistryUrls(json);
            }

            @Test
            void shouldSetFromList() {
                var json = Fixtures.fixture("EurekaConfigTest/sample-config-list-of-urls.json");
                parseJsonAndAssertRegistryUrls(json);
            }

            private void parseJsonAndAssertRegistryUrls(String json) {
                var sampleConfig = new JsonHelper().toObject(json, SampleConfig.class);
                assertParsedRegistryUrls(sampleConfig);
            }
        }

        private void assertParsedRegistryUrls(SampleConfig sampleConfig) {
            assertThat(sampleConfig.getEurekaConfig().getRegistryUrls())
                    .isEqualTo("https://eureka-1.acme.com:8761,https://eureka-2.acme.com:8761");
        }
    }

    @Getter
    @Setter
    static class SampleConfig {
        private EurekaConfig eurekaConfig;
    }
}
