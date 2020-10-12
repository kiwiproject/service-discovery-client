package org.kiwiproject.registry.consul.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.net.HostAndPort;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.catalog.CatalogService;
import com.pszymczyk.consul.junit.ConsulExtension;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.registry.consul.config.ConsulRegistrationConfig;
import org.kiwiproject.registry.exception.RegistrationException;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.util.ServiceInfoHelper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@DisplayName("ConsulRegistryService")
@ExtendWith(SoftAssertionsExtension.class)
class ConsulRegistryServiceIntegrationTest {

    // NOTE: Even though this extension uses an AfterAllCallback, it can NOT be static as running all of the tests fail. I'm not sure if this is
    //       something with the extension or with the Nested test classes
    @RegisterExtension
    ConsulExtension CONSUL = new ConsulExtension();

    private ConsulRegistryService service;
    private KiwiEnvironment environment;
    private Consul consul;
    private ConsulRegistrationConfig config;

    @SuppressWarnings("UnstableApiUsage")
    @BeforeEach
    void setUp() {
        consul = Consul.builder()
                .withHostAndPort(HostAndPort.fromParts("localhost", CONSUL.getHttpPort()))
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

            var serviceInstance = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfo())
                    .withStatus(ServiceInstance.Status.UP)
                    .withMetadata(Map.of(
                            "category", "CORE",
                            "leader", "true"
                    ));

            var registeredInstance = service.register(serviceInstance);

            assertThat(registeredInstance).isNotSameAs(serviceInstance);

            var storedInstance = service.getRegisteredServiceInstance();
            assertThat(storedInstance).isNotNull();
            assertThat(storedInstance.getInstanceId()).isNotBlank();

            assertThat(consul.catalogClient().getService(serviceInstance.getServiceName()).getResponse()).isNotEmpty();
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

            var serviceInstance = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceinfoWithHostName("example.com"))
                    .withStatus(ServiceInstance.Status.UP);
            config.setDomainOverride("test");

            service.register(serviceInstance);

            var services = consul.catalogClient().getService(serviceInstance.getServiceName()).getResponse();

            var hostName = services.stream().map(CatalogService::getServiceAddress).findFirst();
            assertThat(hostName).hasValue("example.test");
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
            assertThat(service.updateStatus(ServiceInstance.Status.UP)).isEqualToComparingFieldByField(serviceInstance);
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
