package org.kiwiproject.registry.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServicePaths;

import java.util.List;

@DisplayName("ServiceInfo")
class ServiceInfoTest {

    @Test
    void shouldHaveSomeDefaults() {
        var info = new ServiceInfo() {

            @Override
            public String getName() {
                return "some name";
            }

            @Override
            public String getHostname() {
                return null;
            }

            @Override
            public String getIp() {
                return null;
            }

            @Override
            public List<Port> getPorts() {
                return null;
            }

            @Override
            public String getVersion() {
                return null;
            }
        };

        assertThat(info.humanReadableName()).isEqualTo(info.getName());

        var paths = info.getPaths();
        assertThat(paths.getHomePagePath()).isEqualTo(ServicePaths.DEFAULT_HOMEPAGE_PATH);
        assertThat(paths.getStatusPath()).isEqualTo(ServicePaths.DEFAULT_STATUS_PATH);
        assertThat(paths.getHealthCheckPath()).isEqualTo(ServicePaths.DEFAULT_HEALTHCHECK_PATH);

        assertThat(info.getDescription()).isBlank();
        assertThat(info.getCommitRef()).isEqualTo("Unknown");
        assertThat(info.getMetadata()).isEmpty();
    }
}
