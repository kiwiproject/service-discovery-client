package org.kiwiproject.registry.eureka.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.registry.eureka.config.EurekaRegistrationConfig.DEFAULT_LEASE_EXPIRATION_DURATION_SECONDS;
import static org.kiwiproject.registry.eureka.config.EurekaRegistrationConfig.DEFAULT_LEASE_RENEWAL_INTERVAL_SECONDS;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.DEFAULT_DATA_CENTER_INFO_CLASS;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.DEFAULT_DATA_CENTER_NAME;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.LEASE_DURATION_IN_SECONDS;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.LEASE_RENEWAL_INTERVAL_IN_SECONDS;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertNoContentResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertNotFoundResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;

import com.netflix.appinfo.InstanceInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.eureka.junit.EurekaServerExtension;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.util.ServiceInfoHelper;

import java.util.Map;

@DisplayName("EurekaRestClient")
class EurekaRestClientTest {

    @RegisterExtension
    public static final EurekaServerExtension EUREKA = new EurekaServerExtension();

    private EurekaRestClient client;
    private String eurekaBaseUrl;

    @BeforeEach
    void setUp() {
        client = new EurekaRestClient();
        eurekaBaseUrl = "http://localhost:" + EUREKA.getPort() + "/eureka/v2";
    }

    @AfterEach
    void cleanupEureka() {
        EUREKA.getEurekaServer().getRegistry().cleanupApps();
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
            var eurekaRegistry = EUREKA.getEurekaServer().getRegistry();
            eurekaRegistry.registerApplication("APPID", "INSTANCEID", "FOO-SERVICE", "UP");

            var response = client.findInstance(eurekaBaseUrl, "APPID", "INSTANCEID");

            assertOkResponse(response);
        }

        @Test
        void shouldReturn404ResponseFromEurekaWhenNotFound() {
            var response = client.findInstance(eurekaBaseUrl, "APPID", "INSTANCEID");
            assertNotFoundResponse(response);
        }

    }

    @Nested
    class FindInstanceByVipAddress {

        @Test
        void shouldReturnResponseFromEurekaWhenFound() {
            var eurekaRegistry = EUREKA.getEurekaServer().getRegistry();
            eurekaRegistry.registerApplication("APPID", "INSTANCEID", "FOO", "UP");

            var response = client.findInstancesByVipAddress(eurekaBaseUrl, "FOO");

            assertOkResponse(response);
        }

    }

    @Nested
    class UpdateStatus {

        @Test
        void shouldReturnResponseFromEureka() {
            var eurekaRegistry = EUREKA.getEurekaServer().getRegistry();
            eurekaRegistry.registerApplication("APPID", "INSTANCEID", "FOO", "DOWN");

            var response = client.updateStatus(eurekaBaseUrl, "APPID", "INSTANCEID", ServiceInstance.Status.UP);

            assertOkResponse(response);

            var instance = eurekaRegistry.getRegisteredApplication("APPID").getByInstanceId("INSTANCEID");
            assertThat(instance.getStatus()).isEqualTo(InstanceInfo.InstanceStatus.UP);
        }

    }

    @Nested
    class Unregister {

        @Test
        void shouldReturnResponseFromEureka() {
            var eurekaRegistry = EUREKA.getEurekaServer().getRegistry();
            eurekaRegistry.registerApplication("APPID", "INSTANCEID", "FOO", "UP");

            var response = client.unregister(eurekaBaseUrl, "APPID", "INSTANCEID");

            assertOkResponse(response);
            assertThat(eurekaRegistry.isApplicationRegistered("APPID")).isFalse();
        }

    }

    @Nested
    class SendHeartbeat {

        @Test
        void shouldReturnResponseFromEureka() {
            var eurekaRegistry = EUREKA.getEurekaServer().getRegistry();
            eurekaRegistry.registerApplication("APPID", "INSTANCEID", "FOO", "UP");

            var response = client.sendHeartbeat(eurekaBaseUrl, "APPID", "INSTANCEID");

            assertOkResponse(response);

            assertThat(eurekaRegistry.getHeartbeatCount()).isOne();
        }

    }

    @Nested
    class FindAllInstances {

        @Test
        void shouldReturnResponseFromEureka() {
            var eurekaRegistry = EUREKA.getEurekaServer().getRegistry();
            eurekaRegistry.registerApplication("APPID", "INSTANCEID", "FOO", "UP");

            var response = client.findAllInstances(eurekaBaseUrl);

            assertOkResponse(response);
        }
    }
}
