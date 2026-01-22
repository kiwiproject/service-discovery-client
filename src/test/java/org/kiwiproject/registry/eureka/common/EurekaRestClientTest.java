package org.kiwiproject.registry.eureka.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.registry.eureka.config.EurekaRegistrationConfig.DEFAULT_LEASE_EXPIRATION_DURATION_SECONDS;
import static org.kiwiproject.registry.eureka.config.EurekaRegistrationConfig.DEFAULT_LEASE_RENEWAL_INTERVAL_SECONDS;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.DEFAULT_DATA_CENTER_INFO_CLASS;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.DEFAULT_DATA_CENTER_NAME;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.LEASE_DURATION_IN_SECONDS;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.LEASE_RENEWAL_INTERVAL_IN_SECONDS;
import static org.kiwiproject.registry.eureka.util.EurekaTestDataHelper.newEurekaContainer;
import static org.kiwiproject.registry.eureka.util.EurekaTestDataHelper.registerInstanceAndAwaitVisibility;
import static org.kiwiproject.registry.eureka.util.EurekaTestDataHelper.sampleInstance;
import static org.kiwiproject.registry.util.InstanceIdGenerator.uniqueInstanceId;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertNoContentResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertNotFoundResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.jaxrs.KiwiGenericTypes;
import org.kiwiproject.registry.eureka.util.EurekaTestDataHelper;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.util.ServiceInfoHelper;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

@DisplayName("EurekaRestClient")
@Testcontainers
@Slf4j
class EurekaRestClientTest {

    @Container
    public static final GenericContainer<?> EUREKA = newEurekaContainer(LOG);

    private EurekaRestClient client;
    private String eurekaBaseUrl;

    @BeforeEach
    void setUp() {
        client = new EurekaRestClient();
        eurekaBaseUrl = EurekaTestDataHelper.eurekaUrl(EUREKA);

        EurekaTestDataHelper.waitForEurekaToStart(eurekaBaseUrl);
    }

    @AfterEach
    void cleanUp() {
        EurekaTestDataHelper.clearAllInstances(eurekaBaseUrl);
    }

    @Nested
    class Register {

        @Test
        void shouldReturnResponseFromEureka() {
            var service = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfo())
                    .withStatus(ServiceInstance.Status.STARTING);

            var instance = EurekaInstance.fromServiceInstance(service)
                    .withApp("appId")
                    .withDataCenterInfo(Map.of(
                            "name", DEFAULT_DATA_CENTER_NAME,
                            "@class", DEFAULT_DATA_CENTER_INFO_CLASS
                    ))
                    .withLeaseInfo(Map.of(
                            LEASE_DURATION_IN_SECONDS, DEFAULT_LEASE_EXPIRATION_DURATION_SECONDS,
                            LEASE_RENEWAL_INTERVAL_IN_SECONDS, DEFAULT_LEASE_RENEWAL_INTERVAL_SECONDS
                    ));

            var response = client.register(eurekaBaseUrl, "appId", instance);

            assertNoContentResponse(response);
        }

    }

    @Nested
    class FindInstance {

        @Test
        void shouldReturnResponseFromEurekaWhenFound() {
            var appId = "APPID";
            var instanceId = uniqueInstanceId();
            registerInstanceAndAwaitVisibility(sampleInstance(appId, instanceId, "FOO-SERVICE", ServiceInstance.Status.UP), eurekaBaseUrl);

            var response = client.findInstance(eurekaBaseUrl, appId, instanceId);

            assertOkResponse(response);
        }

        @Test
        void shouldReturn404ResponseFromEurekaWhenNotFound() {
            var response = client.findInstance(eurekaBaseUrl, "APPID", uniqueInstanceId());
            assertNotFoundResponse(response);
        }

    }

    @Nested
    class FindInstanceByVipAddress {

        @Test
        void shouldReturnResponseFromEurekaWhenFound() {
            var vipAddress = "foo-service";
            registerInstanceAndAwaitVisibility(sampleInstance("APPID", uniqueInstanceId(), vipAddress, ServiceInstance.Status.UP), eurekaBaseUrl);

            var response = client.findInstancesByVipAddress(eurekaBaseUrl, vipAddress);

            assertOkResponse(response);
        }

    }

    @Nested
    class UpdateStatus {

        @Test
        void shouldReturnResponseFromEureka() {
            var appId = "APPID";
            var instanceId = uniqueInstanceId();
            registerInstanceAndAwaitVisibility(sampleInstance(appId, instanceId, "foo-service", ServiceInstance.Status.DOWN), eurekaBaseUrl);

            var response = client.updateStatus(eurekaBaseUrl, appId, instanceId, ServiceInstance.Status.UP);

            assertOkResponse(response);

            try (var instanceResponse = client.findInstance(eurekaBaseUrl, appId, instanceId)) {
                var eurekaResponse = instanceResponse.readEntity(KiwiGenericTypes.MAP_OF_STRING_TO_OBJECT_GENERIC_TYPE);
                var instance = EurekaResponseParser.parseEurekaInstanceResponse(eurekaResponse);
                assertThat(instance.getStatus()).isEqualTo("UP");
            }
        }

    }

    @Nested
    class Unregister {

        @Test
        void shouldReturnResponseFromEureka() {
            var appId = "APPID";
            var instanceId = uniqueInstanceId();
            registerInstanceAndAwaitVisibility(sampleInstance(appId, instanceId, "foo-service", ServiceInstance.Status.UP), eurekaBaseUrl);

            var response = client.unregister(eurekaBaseUrl, appId, instanceId);

            assertOkResponse(response);

            var instanceResponse = client.findInstance(eurekaBaseUrl, appId, instanceId);
            assertNotFoundResponse(instanceResponse);
        }

    }

    @Nested
    class SendHeartbeat {

        @Test
        void shouldReturnResponseFromEureka() {
            var appId = "APPID";
            var instanceId = uniqueInstanceId();
            registerInstanceAndAwaitVisibility(sampleInstance(appId, instanceId, "foo-service", ServiceInstance.Status.UP), eurekaBaseUrl);

            var response = client.sendHeartbeat(eurekaBaseUrl, appId, instanceId);

            assertOkResponse(response);
        }

    }

    @Nested
    class FindAllInstances {

        @Test
        void shouldReturnResponseFromEureka() {
            registerInstanceAndAwaitVisibility(sampleInstance("APPID", uniqueInstanceId(), "foo-service", ServiceInstance.Status.UP), eurekaBaseUrl);

            var response = client.findAllInstances(eurekaBaseUrl);

            assertOkResponse(response);
        }
    }
}
