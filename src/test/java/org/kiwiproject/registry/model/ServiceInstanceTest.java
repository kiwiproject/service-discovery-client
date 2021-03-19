package org.kiwiproject.registry.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;
import org.kiwiproject.registry.util.ServiceInfoHelper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@DisplayName("ServiceInstance")
class ServiceInstanceTest {

    @Test
    void shouldBeAbleToBeCreatedFromServiceInfo() {
        var serviceInfo = ServiceInfoHelper.buildTestServiceInfo();

        var instance = ServiceInstance.fromServiceInfo(serviceInfo);

        assertThat(instance.getInstanceId()).isBlank();
        assertThat(instance.getServiceName()).isEqualTo(serviceInfo.getName());
        assertThat(instance.getHostName()).isEqualTo(serviceInfo.getHostname());
        assertThat(instance.getIp()).isEqualTo(serviceInfo.getIp());
        assertThat(instance.getPorts()).isEqualTo(serviceInfo.getPorts());
        assertThat(instance.getPaths()).isEqualTo(serviceInfo.getPaths());
        assertThat(instance.getStatus()).isNull();
        assertThat(instance.getCommitRef()).isEqualTo(serviceInfo.getCommitRef());
        assertThat(instance.getDescription()).isEqualTo(serviceInfo.getDescription());
        assertThat(instance.getVersion()).isEqualTo(serviceInfo.getVersion());
        assertThat(instance.getMetadata()).isEqualTo(serviceInfo.getMetadata());
    }

    @Test
    void shouldCopyMetadata() {
        var metadata = Map.of(
                "port", "80",
                "securePort", "0",
                "adminPort", "81",
                "sdkVersion", "0.1.0"
        );
        var serviceInfo = ServiceInfoHelper.buildTestServiceInfo(metadata);

        var instance = ServiceInstance.fromServiceInfo(serviceInfo);

        assertThat(instance.getMetadata()).isEqualTo(metadata);

        assertThatThrownBy(() -> instance.getMetadata().put("newKey", "newValue"))
                .describedAs("metadata should be unmodifiable")
                .isExactlyInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldAllowSettingCertainFieldsWithWithers() {
        var instance = ServiceInstance.builder()
                .build()
                .withInstanceId("instance")
                .withStatus(ServiceInstance.Status.DOWN);

        assertThat(instance.getInstanceId()).isEqualTo("instance");
        assertThat(instance.getStatus()).isEqualTo(ServiceInstance.Status.DOWN);
    }

    @RepeatedTest(10)
    void shouldConvertUpSinceToMillis() {
        var startInstant = Instant.now().minusSeconds(ThreadLocalRandom.current().nextInt(60));

        var instance = ServiceInstance.builder()
                .upSince(startInstant)
                .build();

        assertThat(instance.getUpSinceMillis())
                .isEqualTo(instance.getUpSince().toEpochMilli());
    }

    @Test
    void shouldNotReturnNullUpSince() {
        var instance = ServiceInstance.builder().build();

        assertThat(instance.getUpSince()).isEqualTo(Instant.EPOCH);
        assertThat(instance.getUpSinceMillis()).isZero();
    }

    @Nested
    class Ports {

        private ServiceInstance instance;

        @BeforeEach
        void setUp() {
            instance = ServiceInstance.builder()
                    .ports(List.of(
                            Port.of(9090, PortType.APPLICATION, Security.NOT_SECURE),
                            Port.of(9091, PortType.APPLICATION, Security.SECURE),
                            Port.of(9190, PortType.ADMIN, Security.NOT_SECURE),
                            Port.of(9191, PortType.ADMIN, Security.SECURE)
                    ))
                    .build();
        }

        @Test
        void shouldGetApplicationPort_PreferringSecure() {
            assertThat(instance.getApplicationPort())
                    .isEqualTo(Port.of(9091, PortType.APPLICATION, Security.SECURE));
        }

        @Test
        void shouldGetAdminPort_PreferringSecure() {
            assertThat(instance.getAdminPort())
                    .isEqualTo(Port.of(9191, PortType.ADMIN, Security.SECURE));
        }
    }

}
