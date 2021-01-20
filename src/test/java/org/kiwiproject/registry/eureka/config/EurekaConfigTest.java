package org.kiwiproject.registry.eureka.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EurekaConfig")
class EurekaConfigTest {

    @Nested
    class GetRegistryUrls {

        @Test
        void shouldReturnOriginalUrlsIfDomainIsNotOverridden() {
            var config = new EurekaConfig();
            config.setRegistryUrls("http://eureka.test:8761/eureka,http://eureka2.test:8761/eureka");

            assertThat(config.getRegistryUrls()).isEqualTo("http://eureka.test:8761/eureka,http://eureka2.test:8761/eureka");
        }

        @Test
        void shouldReturnUrlsWithTheDomainChanged_WhenDomainIsOverridden() {
            var config = new EurekaConfig();
            config.setRegistryUrls("http://eureka.test:8761/eureka,http://eureka2.test:8761/eureka");
            config.setDomainOverride("prod");

            assertThat(config.getRegistryUrls()).isEqualTo("http://eureka.prod:8761/eureka,http://eureka2.prod:8761/eureka");
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
}
