package org.kiwiproject.registry.eureka.common;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.model.ServicePaths;
import org.kiwiproject.registry.util.ServiceInfoHelper;

import java.util.Map;

@DisplayName("EurekaInstance")
@ExtendWith(SoftAssertionsExtension.class)
class EurekaInstanceTest {

    @Nested
    class FromServiceInstance {

        @SuppressWarnings("unchecked")
        @Test
        void shouldReturnNewInstanceWhenOnlyNonSecurePorts(SoftAssertions softly) {
            var service = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfo()).withStatus(ServiceInstance.Status.STARTING);
            var instance = EurekaInstance.fromServiceInstance(service);

            softly.assertThat(instance.getHostName()).isEqualTo(service.getHostName());
            softly.assertThat(instance.getIpAddr()).isEqualTo(service.getIp());
            softly.assertThat(instance.getVipAddress()).isEqualTo(service.getServiceName());
            softly.assertThat(instance.getSecureVipAddress()).isEqualTo(service.getServiceName());
            softly.assertThat(instance.getStatus()).isEqualTo(service.getStatus().name());
            softly.assertThat(instance.getPort()).contains(
                    entry("$", 80),
                    entry("@enabled", true)
            );
            softly.assertThat(instance.getSecurePort()).contains(
                    entry("$", 0),
                    entry("@enabled", false)
            );
            softly.assertThat(instance.getAdminPort()).isZero();
            softly.assertThat(instance.getHomePageUrl()).isEqualTo("http://localhost:80" + ServicePaths.DEFAULT_HOMEPAGE_PATH);
            softly.assertThat(instance.getStatusPageUrl()).isEqualTo("http://localhost:0" + ServicePaths.DEFAULT_STATUS_PATH);
            softly.assertThat(instance.getHealthCheckUrl()).isEqualTo("http://localhost:0" + ServicePaths.DEFAULT_HEALTHCHECK_PATH);
            softly.assertThat(instance.getMetadata()).contains(
                    entry("commitRef", service.getCommitRef()),
                    entry("description", service.getDescription()),
                    entry("version", service.getVersion())
            );
            softly.assertThat(instance.getLeaseInfo()).isNullOrEmpty();
            softly.assertThat(instance.getDataCenterInfo()).isNullOrEmpty();
        }

        @Test
        void shouldPreferSecurePortOverNotSecure() {
            var serviceInfo = ServiceInfoHelper.buildTestServiceInfo();
            serviceInfo.getPorts().clear();
            serviceInfo.getPorts().add(Port.of(8080, PortType.APPLICATION, Security.NOT_SECURE));
            serviceInfo.getPorts().add(Port.of(8081, PortType.APPLICATION, Security.SECURE));
            serviceInfo.getPorts().add(Port.of(8082, PortType.ADMIN, Security.NOT_SECURE));
            serviceInfo.getPorts().add(Port.of(8083, PortType.ADMIN, Security.SECURE));

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

        @Test
        void shouldMapDefaultMetadata() {
            var serviceInstance = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfo())
                    .withStatus(ServiceInstance.Status.UP);

            var eurekaInstance = EurekaInstance.fromServiceInstance(serviceInstance);

            assertThat(eurekaInstance.getMetadata()).containsOnly(
                    entry("commitRef", serviceInstance.getCommitRef()),
                    entry("description", serviceInstance.getDescription()),
                    entry("version", serviceInstance.getVersion())
            );
        }

        @Test
        void shouldMapCustomMetadataWithDefaultMetadata() {
            var serviceInstance = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfo())
                    .withStatus(ServiceInstance.Status.UP)
                    .withMetadata(Map.of("category", "CORE"));

            var eurekaInstance = EurekaInstance.fromServiceInstance(serviceInstance);

            assertThat(eurekaInstance.getMetadata()).containsOnly(
                    entry("commitRef", serviceInstance.getCommitRef()),
                    entry("description", serviceInstance.getDescription()),
                    entry("version", serviceInstance.getVersion()),
                    entry("category", "CORE")
            );
        }
    }

    @Nested
    class ToServiceInstance {

        @Test
        void shouldCreateAServiceInstanceWithEurekaInstanceData() {
            var eurekaInstance = EurekaInstance.builder()
                    .app("appId")
                    .status("UP")
                    .hostName("localhost")
                    .ipAddr("127.0.0.1")
                    .vipAddress("test-service")
                    .secureVipAddress("test-service")
                    .port(Map.of("$", 8080, "@enabled", true))
                    .securePort(Map.of("$", 0, "@enabled", false))
                    .adminPort(8081)
                    .homePageUrl("http://localhost:8080/api")
                    .statusPageUrl("http://localhost:8081/status")
                    .healthCheckUrl("http://localhost:8081/health")
                    .metadata(Map.of(
                            "commitRef", "abcdef",
                            "description", "some cool service",
                            "version", "0.1.0"
                    ))
                    .build();

            var serviceInstance = eurekaInstance.toServiceInstance();

            assertThat(serviceInstance.getInstanceId()).isEqualTo(eurekaInstance.getHostName());
            assertThat(serviceInstance.getStatus()).isEqualTo(ServiceInstance.Status.UP);
            assertThat(serviceInstance.getServiceName()).isEqualTo(eurekaInstance.getVipAddress());
            assertThat(serviceInstance.getHostName()).isEqualTo(eurekaInstance.getHostName());
            assertThat(serviceInstance.getIp()).isEqualTo(eurekaInstance.getIpAddr());

            assertThat(serviceInstance.getPorts())
                    .extracting("number", "secure", "type")
                    .containsExactlyInAnyOrder(
                            tuple(8080, Security.NOT_SECURE, PortType.APPLICATION),
                            tuple(8081, Security.NOT_SECURE, PortType.ADMIN)
                    );

            assertThat(serviceInstance.getPaths())
                    .usingRecursiveComparison()
                    .isEqualTo(ServicePaths.builder()
                            .homePagePath("/api")
                            .statusPath("/status")
                            .healthCheckPath("/health")
                            .build());

            assertThat(serviceInstance.getCommitRef()).isEqualTo("abcdef");
            assertThat(serviceInstance.getDescription()).isEqualTo("some cool service");
            assertThat(serviceInstance.getVersion()).isEqualTo("0.1.0");
        }

        @Test
        void shouldCreateAServiceInstanceWithEurekaInstanceRawData() {
            var eurekaInstance = EurekaInstance.builder()
                    .app("appId")
                    .status("UP")
                    .hostName("localhost")
                    .ipAddr("127.0.0.1")
                    .vipAddress("test-service")
                    .secureVipAddress("test-service")
                    .port(Map.of("$", 8080, "@enabled", true))
                    .securePort(Map.of("$", 0, "@enabled", false))
                    .adminPort(8081)
                    .homePageUrl("http://localhost:8080/api")
                    .statusPageUrl("http://localhost:8081/status")
                    .healthCheckUrl("http://localhost:8081/health")
                    .metadata(Map.of(
                            "commitRef", "abcdef",
                            "description", "some cool service",
                            "version", "0.1.0"
                    ))
                    .build();

            eurekaInstance = eurekaInstance.withRawResponse(JSON_HELPER.convertToMap(eurekaInstance));

            var serviceInstance = eurekaInstance.toServiceInstance(true);

            assertThat(serviceInstance.getNativeRegistryData()).isEqualTo(eurekaInstance.getRawResponse());
        }

        @Test
        void shouldResolveDefaultPathsIfNotSet() {
            var eurekaInstance = EurekaInstance.builder()
                    .app("appId")
                    .status("UP")
                    .hostName("localhost")
                    .ipAddr("127.0.0.1")
                    .vipAddress("test-service")
                    .secureVipAddress("test-service")
                    .port(Map.of("$", 8080, "@enabled", true))
                    .securePort(Map.of("$", 0, "@enabled", false))
                    .adminPort(8081)
                    .metadata(Map.of(
                            "commitRef", "abcdef",
                            "description", "some cool service",
                            "version", "0.1.0"
                    ))
                    .build();

            var serviceInstance = eurekaInstance.toServiceInstance();

            assertThat(serviceInstance.getPaths())
                    .usingRecursiveComparison()
                    .isEqualTo(ServicePaths.builder()
                            .homePagePath(ServicePaths.DEFAULT_HOMEPAGE_PATH)
                            .statusPath(ServicePaths.DEFAULT_STATUS_PATH)
                            .healthCheckPath(ServicePaths.DEFAULT_HEALTHCHECK_PATH)
                            .build());
        }

        @Test
        void shouldExcludeAdminPortIfNotSet() {
            var eurekaInstance = EurekaInstance.builder()
                    .app("appId")
                    .status("UP")
                    .hostName("localhost")
                    .ipAddr("127.0.0.1")
                    .vipAddress("test-service")
                    .secureVipAddress("test-service")
                    .port(Map.of("$", 8080, "@enabled", true))
                    .securePort(Map.of("$", 0, "@enabled", false))
                    .metadata(Map.of(
                            "commitRef", "abcdef",
                            "description", "some cool service",
                            "version", "0.1.0"
                    ))
                    .build();

            var serviceInstance = eurekaInstance.toServiceInstance();

            assertThat(serviceInstance.getPorts())
                    .extracting("number", "secure", "type")
                    .containsExactlyInAnyOrder(
                            tuple(8080, Security.NOT_SECURE, PortType.APPLICATION)
                    );
        }

        @Test
        void shouldMapDefaultMetadataBack() {
            var eurekaInstance = EurekaInstance.builder()
                    .app("appId")
                    .status("UP")
                    .hostName("localhost")
                    .ipAddr("127.0.0.1")
                    .vipAddress("test-service")
                    .secureVipAddress("test-service")
                    .port(Map.of("$", 8080, "@enabled", true))
                    .securePort(Map.of("$", 0, "@enabled", false))
                    .adminPort(8081)
                    .homePageUrl("http://localhost:8080/api")
                    .statusPageUrl("http://localhost:8081/status")
                    .healthCheckUrl("http://localhost:8081/health")
                    .metadata(Map.of(
                            "commitRef", "abcdef",
                            "description", "some cool service",
                            "version", "0.1.0"
                    ))
                    .build();

            var serviceInstance = eurekaInstance.toServiceInstance();

            assertThat(serviceInstance.getCommitRef()).isEqualTo("abcdef");
            assertThat(serviceInstance.getDescription()).isEqualTo("some cool service");
            assertThat(serviceInstance.getVersion()).isEqualTo("0.1.0");
            assertThat(serviceInstance.getMetadata()).isEmpty();
        }

        @Test
        void shouldMapCustomMetadataIgnoringDefaults() {
            var eurekaInstance = EurekaInstance.builder()
                    .app("appId")
                    .status("UP")
                    .hostName("localhost")
                    .ipAddr("127.0.0.1")
                    .vipAddress("test-service")
                    .secureVipAddress("test-service")
                    .port(Map.of("$", 8080, "@enabled", true))
                    .securePort(Map.of("$", 0, "@enabled", false))
                    .adminPort(8081)
                    .homePageUrl("http://localhost:8080/api")
                    .statusPageUrl("http://localhost:8081/status")
                    .healthCheckUrl("http://localhost:8081/health")
                    .metadata(Map.of(
                            "commitRef", "abcdef",
                            "description", "some cool service",
                            "version", "0.1.0",
                            "category", "CORE"
                    ))
                    .build();

            var serviceInstance = eurekaInstance.toServiceInstance();

            assertThat(serviceInstance.getCommitRef()).isEqualTo("abcdef");
            assertThat(serviceInstance.getDescription()).isEqualTo("some cool service");
            assertThat(serviceInstance.getVersion()).isEqualTo("0.1.0");
            assertThat(serviceInstance.getMetadata()).containsOnly(entry("category", "CORE"));
        }
    }
}
