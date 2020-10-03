package org.kiwiproject.registry.eureka.common;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.model.ServicePaths;
import org.kiwiproject.registry.util.ServiceInfoHelper;

@DisplayName("EurekaInstance")
class EurekaInstanceTest {

    @Nested
    class FromServiceInstance {

        @Test
        void shouldReturnNewInstanceWhenOnlyNonSecurePorts() {
            var service = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfo()).withStatus(ServiceInstance.Status.STARTING);
            var instance = EurekaInstance.fromServiceInstance(service);

            assertThat(instance.getHostName()).isEqualTo(service.getHostName());
            assertThat(instance.getIpAddr()).isEqualTo(service.getIp());
            assertThat(instance.getVipAddress()).isEqualTo(service.getServiceName());
            assertThat(instance.getSecureVipAddress()).isEqualTo(service.getServiceName());
            assertThat(instance.getStatus()).isEqualTo(service.getStatus().name());
            assertThat(instance.getPort()).contains(
                    entry("$", 80),
                    entry("@enabled", true)
            );
            assertThat(instance.getSecurePort()).contains(
                    entry("$", 0),
                    entry("@enabled", false)
            );
            assertThat(instance.getAdminPort()).isZero();
            assertThat(instance.getHomePageUrl()).isEqualTo("http://localhost:80" + ServicePaths.DEFAULT_HOMEPAGE_PATH);
            assertThat(instance.getStatusPageUrl()).isEqualTo("http://localhost:0" + ServicePaths.DEFAULT_STATUS_PATH);
            assertThat(instance.getHealthCheckUrl()).isEqualTo("http://localhost:0" + ServicePaths.DEFAULT_HEALTHCHECK_PATH);
            assertThat(instance.getMetadata()).contains(
                    entry("commitRef", service.getCommitRef()),
                    entry("description", service.getDescription()),
                    entry("version", service.getVersion())
            );
            assertThat(instance.getLeaseInfo()).isNullOrEmpty();
            assertThat(instance.getDataCenterInfo()).isNullOrEmpty();
        }

        @Test
        void shouldPreferSecurePortOverNotSecure() {
            var serviceInfo = ServiceInfoHelper.buildTestServiceInfo();
            serviceInfo.getPorts().clear();
            serviceInfo.getPorts().add(Port.builder().number(8080).type(Port.PortType.APPLICATION).secure(Port.Security.NOT_SECURE).build());
            serviceInfo.getPorts().add(Port.builder().number(8081).type(Port.PortType.APPLICATION).secure(Port.Security.SECURE).build());
            serviceInfo.getPorts().add(Port.builder().number(8082).type(Port.PortType.ADMIN).secure(Port.Security.NOT_SECURE).build());
            serviceInfo.getPorts().add(Port.builder().number(8083).type(Port.PortType.ADMIN).secure(Port.Security.SECURE).build());

            var service = ServiceInstance.fromServiceInfo(serviceInfo).withStatus(ServiceInstance.Status.STARTING);
            var instance = EurekaInstance.fromServiceInstance(service);

            assertThat(instance.getPort()).contains(
                    entry("$", 8080),
                    entry("@enabled", true)
            );
            assertThat(instance.getSecurePort()).contains(
                    entry("$", 8081),
                    entry("@enabled", true)
            );
            assertThat(instance.getAdminPort()).isEqualTo(8083);
            assertThat(instance.getHomePageUrl()).isEqualTo("https://localhost:8081" + ServicePaths.DEFAULT_HOMEPAGE_PATH);
            assertThat(instance.getStatusPageUrl()).isEqualTo("https://localhost:8083" + ServicePaths.DEFAULT_STATUS_PATH);
            assertThat(instance.getHealthCheckUrl()).isEqualTo("https://localhost:8083" + ServicePaths.DEFAULT_HEALTHCHECK_PATH);
        }
    }
}
