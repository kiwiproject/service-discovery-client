package org.kiwiproject.registry.consul.server;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.registry.consul.util.ConsulTestcontainers.consulHostAndPort;
import static org.kiwiproject.registry.consul.util.ConsulTestcontainers.newConsulContainer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.net.HostAndPort;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import org.assertj.core.api.Condition;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.registry.consul.config.ConsulRegistrationConfig;
import org.kiwiproject.registry.exception.RegistrationException;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.util.ServiceInfoHelper;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;

@DisplayName("ConsulRegistryService")
@Testcontainers
@ExtendWith(SoftAssertionsExtension.class)
@Slf4j
class ConsulRegistryServiceIntegrationTest {

    @Container
    public static final ConsulContainer CONSUL = newConsulContainer();

    private ConsulRegistryService service;
    private KiwiEnvironment environment;
    private Consul consul;
    private ConsulRegistrationConfig config;

    @BeforeEach
    void setUp() {
        var consulHostAndPort = consulHostAndPort(CONSUL);

        consul = Consul.builder()
                .withHostAndPort(HostAndPort.fromParts(consulHostAndPort.getHost(), consulHostAndPort.getPort()))
                .build();
        environment = mock(KiwiEnvironment.class);
        config = new ConsulRegistrationConfig();
        config.setMetadataTags(List.of("leader"));
        service = new ConsulRegistryService(consul, config, environment);
    }

    @Nested
    class CreateCandidateFrom {

        @Test
        void shouldCreateAServiceInstanceFromTheServiceInfo(SoftAssertions softly) {
            var serviceInfo = ServiceInfoHelper.buildTestServiceInfo();

            var serviceInstance = service.createCandidateFrom(serviceInfo);

            softly.assertThat(serviceInstance.getInstanceId()).isBlank();
            softly.assertThat(serviceInstance.getServiceName()).isEqualTo(serviceInfo.getName());
            softly.assertThat(serviceInstance.getPorts()).isEqualTo(serviceInfo.getPorts());
            softly.assertThat(serviceInstance.getVersion()).isEqualTo(serviceInfo.getVersion());
            softly.assertThat(serviceInstance.getDescription()).isEqualTo(serviceInfo.getDescription());
            softly.assertThat(serviceInstance.getCommitRef()).isEqualTo(serviceInfo.getCommitRef());
            softly.assertThat(serviceInstance.getPaths()).isEqualTo(serviceInfo.getPaths());
            softly.assertThat(serviceInstance.getIp()).isEqualTo(serviceInfo.getIp());
            softly.assertThat(serviceInstance.getStatus()).isEqualTo(ServiceInstance.Status.UP);
        }

    }

    @Nested
    class Register {

        private String serviceName;

        // If set by a test, will be deregistered afer test completion
        private ServiceInstance registeredInstance;

        @BeforeEach
        void setUp() {
            serviceName = "test-service-" + ThreadLocalRandom.current().nextInt(0, 1_000);
        }

        @AfterEach
        void tearDown() {
            if (nonNull(registeredInstance)) {
                var serviceId = registeredInstance.getInstanceId();
                try {
                    consul.agentClient().deregister(serviceId);
                } catch (Exception e) {
                    LOG.warn("Error deregistering service with ID: {}", serviceId, e);
                }
            }
        }

        @Test
        void shouldThrowIllegalStateException() {
            service.registeredService.set(ServiceInstance.builder().build());

            assertThatThrownBy(() -> service.register(ServiceInstance.builder().build()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot register. Already managing a registered instance");
        }

        @Test
        void shouldRegisterAndReturnRegisteredInstance() {
            var now = Instant.now();
            when(environment.currentInstant()).thenReturn(now);

            var serviceInfo = ServiceInfoHelper.buildTestServiceInfo(serviceName, "service-1.acme.com");
            var serviceInstance = ServiceInstance.fromServiceInfo(serviceInfo)
                    .withStatus(ServiceInstance.Status.UP)
                    .withMetadata(Map.of(
                            "category", "CORE",
                            "leader", "true"
                    ));

            registeredInstance = service.register(serviceInstance);

            assertThat(registeredInstance).isNotSameAs(serviceInstance);

            var storedInstance = service.getRegisteredServiceInstance();
            assertThat(storedInstance).isNotNull();
            assertThat(storedInstance.getInstanceId()).isNotBlank();

            var services = consul.catalogClient().getService(serviceInstance.getServiceName()).getResponse();
            assertThat(services).hasSize(1);

            var foundService = first(services);
            assertThat(foundService.getServiceId()).isEqualTo(registeredInstance.getInstanceId());
            assertThat(foundService.getServiceName()).isEqualTo(registeredInstance.getServiceName());
            assertThat(foundService.getServiceAddress()).isEqualTo(registeredInstance.getHostName());
            assertThat(foundService.getAddress()).isEqualTo(registeredInstance.getIp());
            assertThat(foundService.getServicePort()).isEqualTo(registeredInstance.getApplicationPort().getNumber());
            assertThat(foundService.getServiceMeta()).isNotNull();

            Map<String, String> metadata = foundService.getServiceMeta();
            assertThat(metadata).contains(
                entry("category", "CORE"),
                entry("leader", "true"),
                entry("adminPort", String.valueOf(registeredInstance.getAdminPort().getNumber())),
                entry("scheme", "http"),
                entry("ipAddress", registeredInstance.getIp()),
                entry("statusPath", registeredInstance.getPaths().getStatusPath()),
                entry("healthCheckPath", registeredInstance.getPaths().getHealthCheckPath()),
                entry("version", registeredInstance.getVersion()),
                entry("commitRef", registeredInstance.getCommitRef())
            );

            var timestampCondition = new Condition<String>(
                    value -> {
                        var serviceUpEpochMillis = Long.parseLong(value);
                        var diff = serviceUpEpochMillis - now.toEpochMilli();
                        return diff < 2_000;
                    }, "serviceUpTimestamp should be close to now (%d) but was more than 2 seconds different", now.toEpochMilli());
            assertThat(metadata).hasEntrySatisfying("serviceUpTimestamp", timestampCondition);
        }

        @Test
        void shouldRetryRegisterAndThrowExceptionIfAllTriesExpire() {
            var badConsul = mock(Consul.class);
            var serviceToFail = new ConsulRegistryService(badConsul, new ConsulRegistrationConfig(), environment);

            doThrow(new RuntimeException("Oops")).when(badConsul).agentClient();

            var now = Instant.now();
            when(environment.currentInstant()).thenReturn(now);

            var serviceInstance = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfo())
                    .withStatus(ServiceInstance.Status.UP);

            assertThatThrownBy(() -> serviceToFail.register(serviceInstance))
                    .isInstanceOf(RegistrationException.class)
                    .hasMessageStartingWith("Unable to register service " + serviceInstance.getServiceName() + ", id ")
                    .hasMessageEndingWith(" with Consul after 60 attempts");

            assertThat(serviceToFail.getRegisteredServiceInstance()).isNull();
        }

        @Test
        void shouldRegisterAndReturnRegisteredInstance_OverridingDomainIfConfiguredToDoSo() {
            var now = Instant.now();
            when(environment.currentInstant()).thenReturn(now);

            var serviceInfo = ServiceInfoHelper.buildTestServiceInfo(serviceName, "example.com");
            var serviceInstance = ServiceInstance.fromServiceInfo(serviceInfo)
                    .withStatus(ServiceInstance.Status.UP);
            config.setDomainOverride("test");

            registeredInstance = service.register(serviceInstance);
            assertThat(registeredInstance.getHostName())
                    .describedAs("hostName on registered ServiceInstance should contain the overridden domain")
                    .isEqualTo("example.test");

            var services = consul.catalogClient().getService(serviceInstance.getServiceName()).getResponse();

            assertThat(services).hasSize(1);

            var foundService = first(services);
            var hostName = foundService.getServiceAddress();
            assertThat(hostName)
                    .describedAs("serviceAddress on CatalogService should contain the overridden domain")
                    .isEqualTo("example.test");
        }
    }

    @Nested
    class UpdateStatus {

        @Test
        void shouldReturnNullIfNotRegistered() {
            assertThatThrownBy(() -> service.updateStatus(ServiceInstance.Status.UP))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Can not update status before calling register");
        }

        @Test
        void shouldReturnRegisteredInstanceUntouchedWhenRegistered() {
            var serviceInstance = ServiceInstance.builder().build();
            service.registeredService.set(serviceInstance);
            assertThat(service.updateStatus(ServiceInstance.Status.UP))
                    .usingRecursiveComparison()
                    .isEqualTo(serviceInstance);
        }
    }

    @Nested
    class UnRegister {

        @Test
        void shouldUnRegister() {
            service.registeredService.set(ServiceInstance.builder().serviceName("APPID").instanceId("INSTANCEID").build());
            consul.agentClient().register(ImmutableRegistration.builder().name("APPID").id("INSTANCEID").address("localhost").build());

            service.unregister();

            assertThat(service.getRegisteredServiceInstance()).isNull();
            assertThat(consul.catalogClient().getService("APPID").getResponse()).isEmpty();
        }

        @Test
        void shouldThrowIllegalStateExceptionWhenNeverRegistered() {
            assertThatThrownBy(service::unregister)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot unregister since registration was never called");
        }

        @Test
        void shouldRetryUnregisterAndThrowExceptionIfAllTriesExpire() {
            var badConsul = mock(Consul.class);
            var serviceToFail = new ConsulRegistryService(badConsul, new ConsulRegistrationConfig(), environment);
            serviceToFail.registeredService.set(ServiceInstance.builder().serviceName("APPID").instanceId("INSTANCEID").build());

            doThrow(new RuntimeException("Oops")).when(badConsul).agentClient();

            assertThatThrownBy(serviceToFail::unregister)
                    .isInstanceOf(RegistrationException.class)
                    .hasMessage("Error un-registering service APPID, id INSTANCEID");

            assertThat(serviceToFail.getRegisteredServiceInstance()).isNotNull();
        }
    }
}
