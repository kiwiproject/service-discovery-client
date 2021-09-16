package org.kiwiproject.registry.eureka.server;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.kiwiproject.jaxrs.KiwiStandardResponses.standardBadRequestResponse;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.APP_TIMESTAMP_FORMATTER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.netflix.appinfo.InstanceInfo;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.concurrent.TimeUnit;

@DisplayName("EurekaRegistryService")
@ExtendWith(SoftAssertionsExtension.class)
class EurekaRegistryServiceIntegrationTest {

    @RegisterExtension
    public static final EurekaServerExtension EUREKA = new EurekaServerExtension();

    private KiwiEnvironment environment;
    private EurekaRegistryService service;
    private EurekaRegistrationConfig config;

    @BeforeEach
    void setUp() {
        environment = mock(KiwiEnvironment.class);

        var client = new EurekaRestClient();

        config = new EurekaRegistrationConfig();
        config.setHeartbeatIntervalInSeconds(1);
        config.setRegistryUrls("http://localhost:" + EUREKA.getPort() + "/eureka/v2");

        service = new EurekaRegistryService(config, client, environment);
    }

    @AfterEach
    void cleanupEureka() {
        EUREKA.clearRegisteredApps();

        if (nonNull(service.heartbeatExecutor.get())) {
            service.heartbeatExecutor.get().shutdownNow();
            service.heartbeatExecutor.set(null);
        }
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
            softly.assertThat(serviceInstance.getStatus()).isEqualTo(ServiceInstance.Status.STARTING);
            softly.assertThat(serviceInstance.getMetadata()).isNullOrEmpty();
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

            var expectedAppId = serviceInstance.getServiceName().toUpperCase(Locale.getDefault())
                    + "-" + APP_TIMESTAMP_FORMATTER.format(now);

            assertThat(eurekaInstance.getApp()).isEqualTo(expectedAppId);
            assertThat(service.heartbeatExecutor.get()).isNotNull();
            assertThat(EUREKA.getRegisteredApplications()).extracting("name").contains(expectedAppId);

            await().atMost(5, TimeUnit.SECONDS).until(() -> EUREKA.getHeartbeatCount() > 1);

            assertThat(EUREKA.getHeartbeatCount()).isGreaterThan(1);
        }

        @Test
        void shouldRetryRegisterAndThrowExceptionIfAllTriesExpire() {
            var now = Instant.now();
            when(environment.currentInstant()).thenReturn(now);

            var serviceInfo = ServiceInfoHelper.buildTestServiceInfo("FailRegistrationFirstNTimes-61", null);

            var serviceInstance = ServiceInstance.fromServiceInfo(serviceInfo)
                    .withStatus(ServiceInstance.Status.STARTING);

            assertThatThrownBy(() -> service.register(serviceInstance))
                    .isInstanceOf(RegistrationException.class)
                    .hasMessage("Received errors or non-204 responses on ALL 60 attempts to register (via POST) with Eureka");

            assertThat(service.registeredInstance.get()).isNull();

            var appId = serviceInstance.getServiceName().toUpperCase(Locale.getDefault()) + "-" + APP_TIMESTAMP_FORMATTER.format(now);
            assertThat(EUREKA.isApplicationRegistered(appId)).isFalse();
        }

        @Test
        void shouldRetryLookupAfterRegistrationAndThrowExceptionIfAllTriesExpire() {
            // Going to spy the EurekaClient, so I can fake a bad response from the registry lookup
            var eurekaClientSpy = spy(new EurekaRestClient());
            var service = new EurekaRegistryService(config, eurekaClientSpy, environment);

            var now = Instant.now();
            when(environment.currentInstant()).thenReturn(now);

            var serviceInfo = ServiceInfoHelper.buildTestServiceInfoWithHostName("FailAwaitRegistrationFirstNTimes-11");
            var serviceInstance = ServiceInstance.fromServiceInfo(serviceInfo)
                    .withStatus(ServiceInstance.Status.STARTING);

            doReturn(standardBadRequestResponse("Fake bad request")).when(eurekaClientSpy).findInstance(anyString(), anyString(), anyString());

            var appId = serviceInstance.getServiceName().toUpperCase(Locale.getDefault()) + "-" + APP_TIMESTAMP_FORMATTER.format(now);
            assertThatThrownBy(() -> service.register(serviceInstance))
                    .isInstanceOf(RegistrationException.class)
                    .hasMessage("Unable to obtain app " + appId + ", instance " + serviceInstance.getHostName() + " from Eureka during registration after 10 attempts");

            assertThat(service.registeredInstance.get()).isNull();
            assertThat(EUREKA.isApplicationRegistered(appId)).isTrue();
        }

        @Test
        void shouldRetryHeartbeatsIfFailureOccurs() {

            // Going to spy the EurekaClient, so I can fake a bad response from the heartbeat sender
            var eurekaClientSpy = spy(new EurekaRestClient());
            var service = new EurekaRegistryService(config, eurekaClientSpy, environment);

            var now = Instant.now();
            when(environment.currentInstant()).thenReturn(now);

            var serviceInfo = ServiceInfoHelper.buildTestServiceInfoWithHostName("FailHeartbeat-1");
            var serviceInstance = ServiceInstance.fromServiceInfo(serviceInfo)
                    .withStatus(ServiceInstance.Status.STARTING);

            doReturn(standardBadRequestResponse("Bad heartbeat request"))
                    .doCallRealMethod()
                    .when(eurekaClientSpy)
                    .sendHeartbeat(anyString(), anyString(), anyString());

            service.register(serviceInstance);

            var expectedAppId = serviceInstance.getServiceName().toUpperCase(Locale.getDefault())
                    + "-" + APP_TIMESTAMP_FORMATTER.format(now);

            assertThat(EUREKA.getRegisteredApplications()).extracting("name").contains(expectedAppId);

            await().atMost(5, TimeUnit.SECONDS).until(() -> EUREKA.getHeartbeatCount() > 1);

            assertThat(EUREKA.getHeartbeatCount()).isGreaterThan(1);
        }

        @Test
        void shouldRetryHeartbeatsIfFailureOccursUntilThresholdThenTrySelfHeal() {
            var now = Instant.now();
            when(environment.currentInstant()).thenReturn(now);

            var serviceInstance = ServiceInstance.fromServiceInfo(ServiceInfoHelper
                            .buildTestServiceInfoWithHostName("FailHeartbeat-6"))
                    .withStatus(ServiceInstance.Status.STARTING);

            service.register(serviceInstance);
            var eurekaInstance = service.registeredInstance.get();
            var heartbeatExecutor = service.heartbeatExecutor.get();

            var expectedAppId = serviceInstance.getServiceName().toUpperCase(Locale.getDefault())
                    + "-" + APP_TIMESTAMP_FORMATTER.format(now);

            assertThat(EUREKA.getRegisteredApplications()).extracting("name").contains(expectedAppId);

            await().atMost(10, TimeUnit.SECONDS).until(() -> EUREKA.getHeartbeatCount() > 1);

            EUREKA.clearRegisteredApps();

            await().atMost(10, TimeUnit.SECONDS).until(() -> EUREKA.getHeartbeatCount() > 3);

            assertThat(EUREKA.getHeartbeatCount()).isEqualTo(4);

            assertThat(eurekaInstance).isNotSameAs(service.registeredInstance.get());
            assertThat(heartbeatExecutor).isNotSameAs(service.heartbeatExecutor.get());
        }
    }

    @Nested
    class UpdateStatus {
        @Test
        void shouldUpdateStatus() {
            var serviceInstance = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfo())
                    .withStatus(ServiceInstance.Status.STARTING);
            var initialEurekaInstance = EurekaInstance.fromServiceInstance(serviceInstance).withApp("APPID").withStatus("STARTING");
            service.registeredInstance.set(initialEurekaInstance);

            EUREKA.registerApplication(initialEurekaInstance.getApp(), initialEurekaInstance.getInstanceId(), "VIP-SERVICE", "STARTING");

            service.updateStatus(ServiceInstance.Status.UP);

            var updatedEurekaInstance = service.registeredInstance.get();
            assertThat(updatedEurekaInstance).isNotSameAs(initialEurekaInstance);
            assertThat(updatedEurekaInstance.getStatus()).isEqualTo(ServiceInstance.Status.UP.name());

            assertThat(EUREKA.isApplicationRegistered("APPID")).isTrue();

            var instances = EUREKA.getRegisteredApplication("APPID").getInstances();
            assertThat(instances).extracting("status").contains(InstanceInfo.InstanceStatus.UP);
        }

        @Test
        void shouldRetryUpdateStatusAndThrowExceptionIfAllTriesExpire() {

            // Going to spy the EurekaClient, so I can fake a bad response from the status update sender
            var eurekaClientSpy = spy(new EurekaRestClient());
            var service = new EurekaRegistryService(config, eurekaClientSpy, environment);

            var serviceInfo = ServiceInfoHelper.buildTestServiceInfoWithHostName("FailStatusChange");
            var serviceInstance = ServiceInstance.fromServiceInfo(serviceInfo)
                    .withStatus(ServiceInstance.Status.STARTING);
            var initialEurekaInstance = EurekaInstance.fromServiceInstance(serviceInstance).withApp("APPID").withStatus("STARTING");
            service.registeredInstance.set(initialEurekaInstance);

            doReturn(standardBadRequestResponse("Fail status update"))
                    .when(eurekaClientSpy)
                    .updateStatus(anyString(), anyString(), anyString(), any());

            assertThatThrownBy(() -> service.updateStatus(ServiceInstance.Status.DOWN))
                    .isInstanceOf(RegistrationException.class)
                    .hasMessage("Error updating status for app APPID, instance FailStatusChange");

            assertThat(service.registeredInstance.get()).isSameAs(initialEurekaInstance);
        }
    }

    @Nested
    class UnRegister {

        @Test
        void shouldUnRegister() {
            service.registeredInstance.set(EurekaInstance.builder().app("APPID").hostName("INSTANCEID").build());

            EUREKA.registerApplication("APPID", "INSTANCEID", "VIP-SERVICE", "UP");

            service.unregister();

            assertThat(service.registeredInstance.get()).isNull();
            assertThat(EUREKA.isApplicationRegistered("APPID")).isFalse();
        }

        @Test
        void shouldRetryUnregisterAndThrowExceptionIfAllTriesExpire() {
            // Going to spy the EurekaClient, so I can fake a bad response from the unregistering sender
            var eurekaClientSpy = spy(new EurekaRestClient());
            var service = new EurekaRegistryService(config, eurekaClientSpy, environment);

            service.registeredInstance.set(EurekaInstance.builder().app("APPID").hostName("FailUnregister").build());

            doReturn(standardBadRequestResponse("Fail unregister"))
                    .when(eurekaClientSpy)
                    .unregister(anyString(), anyString(), anyString());

            assertThatThrownBy(service::unregister)
                    .isInstanceOf(RegistrationException.class)
                    .hasMessage("Error un-registering app APPID, instance FailUnregister");

            assertThat(service.registeredInstance.get()).isNotNull();
        }
    }
}
