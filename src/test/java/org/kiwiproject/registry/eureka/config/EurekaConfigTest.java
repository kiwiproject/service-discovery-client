package org.kiwiproject.registry.eureka.config;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.json.JsonHelper;
import org.kiwiproject.test.util.Fixtures;
import org.kiwiproject.yaml.YamlHelper;

import java.util.List;

@DisplayName("EurekaConfig")
class EurekaConfigTest {

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
