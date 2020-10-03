package org.kiwiproject.registry.eureka.common;

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
        EUREKA.getEurekaServer().cleanupApps();
    }

    @Nested
    class Register {

        @Test
        void shouldReturnResponseFromEureka() {
            var service = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfo()).withStatus(ServiceInstance.Status.STARTING);
            var instance = EurekaInstance.fromServiceInstance(service);

            var response = client.register(eurekaBaseUrl, "appId", instance);

            assertNoContentResponse(response);
        }

    }

    @Nested
    class FindInstance {

        @Test
        void shouldReturnResponseFromEurekaWhenFound() {
            EUREKA.getEurekaServer().registerApplication(InstanceInfo.Builder.newBuilder()
                    .setAppName("APPID").setInstanceId("INSTANCEID").build());

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
            EUREKA.getEurekaServer().registerApplication(InstanceInfo.Builder.newBuilder()
                    .setAppName("APPID").setVIPAddress("FOO").setInstanceId("INSTANCEID").build());

            var response = client.findInstancesByVipAddress(eurekaBaseUrl, "FOO");

            assertOkResponse(response);
        }

    }

    @Nested
    class UpdateStatus {

        @Test
        void shouldReturnResponseFromEureka() {
            EUREKA.getEurekaServer().registerApplication(InstanceInfo.Builder.newBuilder()
                    .setAppName("APPID").setVIPAddress("FOO").setInstanceId("INSTANCEID").setStatus(InstanceInfo.InstanceStatus.DOWN).build());

            var response = client.updateStatus(eurekaBaseUrl, "APPID", "INSTANCEID", ServiceInstance.Status.UP);

            assertOkResponse(response);
        }

    }

    @Nested
    class Unregister {

        @Test
        void shouldReturnResponseFromEureka() {
            EUREKA.getEurekaServer().registerApplication(InstanceInfo.Builder.newBuilder()
                    .setAppName("APPID").setVIPAddress("FOO").setInstanceId("INSTANCEID").setStatus(InstanceInfo.InstanceStatus.DOWN).build());

            var response = client.unregister(eurekaBaseUrl, "APPID", "INSTANCEID");

            assertOkResponse(response);
        }

    }

    @Nested
    class SendHeartbeat {

        @Test
        void shouldReturnResponseFromEureka() {
            EUREKA.getEurekaServer().registerApplication(InstanceInfo.Builder.newBuilder()
                    .setAppName("APPID")
                    .setVIPAddress("FOO")
                    .setInstanceId("INSTANCEID")
                    .setHostName("localhost")
                    .setStatus(InstanceInfo.InstanceStatus.DOWN)
                    .build());

            var response = client.sendHeartbeat(eurekaBaseUrl, "APPID", "INSTANCEID");

            assertOkResponse(response);
        }

    }
}
