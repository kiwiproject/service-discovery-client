package org.kiwiproject.registry.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ServicePaths")
class ServicePathsTest {

    @Test
    void shouldHaveDefaultsForAllPaths() {
        var paths = ServicePaths.builder().build();

        assertThat(paths.getHomePagePath()).isEqualTo(ServicePaths.DEFAULT_HOMEPAGE_PATH);
        assertThat(paths.getStatusPath()).isEqualTo(ServicePaths.DEFAULT_STATUS_PATH);
        assertThat(paths.getHealthCheckPath()).isEqualTo(ServicePaths.DEFAULT_HEALTHCHECK_PATH);
    }
}
