package org.kiwiproject.registry.eureka.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.APP_TIMESTAMP_FORMATTER;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.appinfo.InstanceInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.eureka.junit.EurekaServerExtension;
import org.kiwiproject.registry.eureka.common.EurekaInstance;
import org.kiwiproject.registry.eureka.common.EurekaRestClient;
import org.kiwiproject.registry.eureka.config.EurekaRegistrationConfig;
import org.kiwiproject.registry.exception.RegistrationException;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.util.ServiceInfoHelper;

import java.time.Instant;
import java.util.Locale;

@DisplayName("EurekaRegistryService")
class EurekaRegistryServiceIntegrationTest {

    @RegisterExtension
    public static final EurekaServerExtension EUREKA = new EurekaServerExtension();

    private KiwiEnvironment environment;
    private EurekaRegistryService service;

    @BeforeEach
    void setUp() {
        environment = mock(KiwiEnvironment.class);

        var client = new EurekaRestClient();

        var config = new EurekaRegistrationConfig();
        config.setRegistryUrls("http://localhost:" + EUREKA.getPort() + "/eureka/v2");

        service = new EurekaRegistryService(config, client, environment);
    }

    @AfterEach
    void cleanupEureka() {
        EUREKA.getEurekaServer().cleanupApps();
    }

    @Nested
    class CreateCandidateFrom {

        @Test
        void shouldCreateAServiceInstanceFromTheServiceInfo() {
            var serviceInfo = ServiceInfoHelper.buildTestServiceInfo();

            var serviceInstance = service.createCandidateFrom(serviceInfo);

            assertThat(serviceInstance.getInstanceId()).isBlank();
            assertThat(serviceInstance.getServiceName()).isEqualTo(serviceInfo.getName());
            assertThat(serviceInstance.getPorts()).isEqualTo(serviceInfo.getPorts());
            assertThat(serviceInstance.getVersion()).isEqualTo(serviceInfo.getVersion());
            assertThat(serviceInstance.getDescription()).isEqualTo(serviceInfo.getDescription());
            assertThat(serviceInstance.getCommitRef()).isEqualTo(serviceInfo.getCommitRef());
            assertThat(serviceInstance.getPaths()).isEqualTo(serviceInfo.getPaths());
            assertThat(serviceInstance.getIp()).isEqualTo(serviceInfo.getIp());
            assertThat(serviceInstance.getStatus()).isEqualTo(ServiceInstance.Status.STARTING);
        }

    }

    @Nested
    class Register {

        @Test
        void shouldThrowIllegalStateException() {
            service.registeredInstance.set(EurekaInstance.builder().build());

            assertThatThrownBy(() -> service.register(ServiceInstance.builder().build()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot register. Already managing a registered instance: ");
        }

        @Test
        void shouldRegisterAndReturnRegisteredInstance() {
            var now = Instant.now();
            when(environment.currentInstant()).thenReturn(now);

            var serviceInstance = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfo())
                    .withStatus(ServiceInstance.Status.STARTING);

            var registeredInstance = service.register(serviceInstance);

            assertThat(registeredInstance).isNotSameAs(serviceInstance);

            var eurekaInstance = service.registeredInstance.get();
            assertThat(eurekaInstance).isNotNull();
            assertThat(eurekaInstance.getApp()).isEqualTo(serviceInstance.getServiceName().toUpperCase(Locale.getDefault())
                    + "-" + APP_TIMESTAMP_FORMATTER.format(now));
        }

        @Test
        void shouldRetryRegisterAndThrowExceptionIfAllTriesExpire() {
            var now = Instant.now();
            when(environment.currentInstant()).thenReturn(now);

            var serviceInstance = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfoWithName("FailRegistrationFirstNTimes-61"))
                    .withStatus(ServiceInstance.Status.STARTING);

            assertThatThrownBy(() -> service.register(serviceInstance))
                    .isInstanceOf(RegistrationException.class)
                    .hasMessage("Received errors or non-204 responses on ALL 60 attempts to register (via POST) with Eureka");

            assertThat(service.registeredInstance.get()).isNull();

            var appId = serviceInstance.getServiceName().toUpperCase(Locale.getDefault()) + "-" + APP_TIMESTAMP_FORMATTER.format(now);
            assertThat(EUREKA.getEurekaServer().getApplicationByName(appId)).isEmpty();
        }

        @Test
        void shouldRetryLookupAfterRegistrationAndThrowExceptionIfAllTriesExpire() {
            var now = Instant.now();
            when(environment.currentInstant()).thenReturn(now);

            var serviceInstance = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceinfoWithHostName("FailAwaitRegistrationFirstNTimes-11"))
                    .withStatus(ServiceInstance.Status.STARTING);

            var appId = serviceInstance.getServiceName().toUpperCase(Locale.getDefault()) + "-" + APP_TIMESTAMP_FORMATTER.format(now);
            assertThatThrownBy(() -> service.register(serviceInstance))
                    .isInstanceOf(RegistrationException.class)
                    .hasMessage("Unable to obtain app " + appId + ", instance " + serviceInstance.getHostName() + " from Eureka during registration after 10 attempts");

            assertThat(service.registeredInstance.get()).isNull();
            assertThat(EUREKA.getEurekaServer().getApplicationByName(appId)).isPresent();
        }
    }

    @Nested
    class UnRegister {

        @Test
        void shouldUnRegister() {
            service.registeredInstance.set(EurekaInstance.builder().app("APPID").hostName("INSTANCEID").build());
            EUREKA.getEurekaServer().registerApplication(InstanceInfo.Builder.newBuilder()
                    .setAppName("APPID").setInstanceId("INSTANCEID").build());

            service.unregister();

            assertThat(service.registeredInstance.get()).isNull();
            assertThat(EUREKA.getEurekaServer().getApplicationByName("APPID")).isEmpty();
        }

        @Test
        void shouldRetryUnregisterAndThrowExceptionIfAllTriesExpire() {
            service.registeredInstance.set(EurekaInstance.builder().app("APPID").hostName("FailUnregister").build());
            EUREKA.getEurekaServer().registerApplication(InstanceInfo.Builder.newBuilder()
                    .setAppName("APPID").setInstanceId("FailUnregister").setHostName("FailUnregister").build());

            assertThatThrownBy(() -> service.unregister())
                    .isInstanceOf(RegistrationException.class)
                    .hasMessage("Error un-registering app APPID, instance FailUnregister");

            assertThat(service.registeredInstance.get()).isNotNull();
            assertThat(EUREKA.getEurekaServer().getApplicationByName("APPID")).isPresent();
        }
    }
}
