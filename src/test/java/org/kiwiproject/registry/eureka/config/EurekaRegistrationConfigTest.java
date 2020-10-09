package org.kiwiproject.registry.eureka.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EurekaRegistrationConfig")
class EurekaRegistrationConfigTest {

    @Test
    void shouldDefaultProperties() {
        var config = new EurekaRegistrationConfig();

        assertThat(config.getExpirationIntervalInSeconds()).isEqualTo(EurekaRegistrationConfig.DEFAULT_LEASE_EXPIRATION_DURATION_SECONDS);
        assertThat(config.getHeartbeatIntervalInSeconds()).isEqualTo(EurekaRegistrationConfig.DEFAULT_LEASE_RENEWAL_INTERVAL_SECONDS);
    }
}
