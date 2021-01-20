package org.kiwiproject.registry.consul.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConsulRegistrationConfig")
class ConsulRegistrationConfigTest {

    @Test
    void shouldHaveDefaultProperties() {
        var config = new ConsulRegistrationConfig();

        assertThat(config.getCheckIntervalInSeconds()).isEqualTo(ConsulRegistrationConfig.DEFAULT_CHECK_INTERVAL_IN_SECONDS);
        assertThat(config.getDeregisterIntervalInMinutes()).isEqualTo(ConsulRegistrationConfig.DEFAULT_DEREGISTER_INTERVAL_IN_MINUTES);
        assertThat(config.isIncludeNativeData()).isFalse();
    }

}
