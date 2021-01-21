package org.kiwiproject.registry.consul.server;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.registry.util.ServiceInfoHelper;

@DisplayName("ConsulHelpers")
class ConsulHelpersTest {

    @Test
    void shouldBuildDefaultMetadataMap() {
        var info = ServiceInfoHelper.buildTestServiceInfo();

        var kiwiEnv = mock(KiwiEnvironment.class);

        var now = System.currentTimeMillis();
        when(kiwiEnv.currentTimeMillis()).thenReturn(now);

        var metadata = ConsulHelpers.buildDefaultMetadataMap(info, kiwiEnv);

        assertThat(metadata).containsOnly(
                entry("scheme", "http"),
                entry("adminPort", "0"),
                entry("serviceUpTimestamp", String.valueOf(now)),
                entry("homePagePath", info.getPaths().getHomePagePath()),
                entry("statusPath", info.getPaths().getStatusPath()),
                entry("healthCheckPath", info.getPaths().getHealthCheckPath()),
                entry("commitRef", info.getCommitRef()),
                entry("description", info.getDescription()),
                entry("version", info.getVersion()),
                entry("ipAddress", info.getIp())
        );
    }
}
