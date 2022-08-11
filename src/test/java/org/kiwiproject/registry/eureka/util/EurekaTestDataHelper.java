package org.kiwiproject.registry.eureka.util;

import static java.util.concurrent.TimeUnit.MINUTES;
import static javax.ws.rs.client.Entity.json;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.awaitility.Awaitility.await;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.collect.KiwiLists.isNotNullOrEmpty;
import static org.kiwiproject.jaxrs.KiwiResponses.successful;
import static org.kiwiproject.registry.eureka.config.EurekaRegistrationConfig.DEFAULT_LEASE_EXPIRATION_DURATION_SECONDS;
import static org.kiwiproject.registry.eureka.config.EurekaRegistrationConfig.DEFAULT_LEASE_RENEWAL_INTERVAL_SECONDS;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.DEFAULT_DATA_CENTER_INFO_CLASS;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.DEFAULT_DATA_CENTER_NAME;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.LEASE_DURATION_IN_SECONDS;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.LEASE_RENEWAL_INTERVAL_IN_SECONDS;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertNoContentResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertNotFoundResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.message.GZipEncoder;
import org.kiwiproject.jaxrs.KiwiGenericTypes;
import org.kiwiproject.registry.eureka.common.EurekaInstance;
import org.kiwiproject.registry.eureka.common.EurekaResponseParser;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.util.ServiceInfoHelper;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class EurekaTestDataHelper {

    public static ImageFromDockerfile eurekaImage() {
        return new ImageFromDockerfile()
                .withFileFromClasspath("Dockerfile", "eureka-server/Dockerfile")
                .withFileFromClasspath("eureka-server-1.10.17.war", "eureka-server/eureka-server-1.10.17.war")
                .withFileFromClasspath("config.properties", "eureka-server/config.properties")
                .withFileFromClasspath("eureka-client-test.properties", "eureka-server/eureka-client-test.properties")
                .withFileFromClasspath("eureka-server-test.properties", "eureka-server/eureka-server-test.properties");
    }

    public static String eurekaUrl(GenericContainer container) {
        var host = container.getHost();
        var port = container.getFirstMappedPort();
        return f("http://{}:{}/eureka/v2", host, port);
    }

    public static void waitForEurekaToStart(String baseUrl) {
        var client = ClientBuilder.newClient().register(GZipEncoder.class);
        await().atMost(1, MINUTES).until(() -> {
            var response = client.target(baseUrl)
                    .path("apps")
                    .request()
                    .accept(APPLICATION_JSON_TYPE)
                    .get();
            LOG.info("Waiting for Eureka to start.  Status: {}", response.getStatus());
            return successful(response);
        });
    }

    public static EurekaInstance sampleInstance(String appName, String hostname, String vipAddress, ServiceInstance.Status status) {
        var service = ServiceInstance.fromServiceInfo(ServiceInfoHelper.buildTestServiceInfo(vipAddress, hostname, new HashMap<>()))
                .withStatus(status);

        return EurekaInstance.fromServiceInstance(service)
                .withApp(appName)
                .withDataCenterInfo(Map.of(
                        "name", DEFAULT_DATA_CENTER_NAME,
                        "@class", DEFAULT_DATA_CENTER_INFO_CLASS
                ))
                .withLeaseInfo(Map.of(
                        LEASE_DURATION_IN_SECONDS, DEFAULT_LEASE_EXPIRATION_DURATION_SECONDS,
                        LEASE_RENEWAL_INTERVAL_IN_SECONDS, DEFAULT_LEASE_RENEWAL_INTERVAL_SECONDS
                ));
    }

    public static void loadInstanceAndWaitForRegistration(String baseUrl) {
        loadInstanceAndWaitForRegistration(sampleInstance("TEST-APP", "localhost", "TEST-APP", ServiceInstance.Status.UP), baseUrl);
    }

    public static void loadInstanceAndWaitForRegistration(EurekaInstance instance, String baseUrl) {
        var client = ClientBuilder.newClient().register(GZipEncoder.class);

        var loadResponse = client.target(baseUrl)
                .path("apps/{appId}")
                .resolveTemplate("appId", instance.getApp())
                .request()
                .accept(APPLICATION_JSON_TYPE)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .post(json(Map.of("instance", instance)));

        assertNoContentResponse(loadResponse);

        await().atMost(1, MINUTES).until(() -> {
            var response = client.target(baseUrl)
                    .path("apps")
                    .request()
                    .accept(APPLICATION_JSON_TYPE)
                    .get();

            if (successful(response)) {
                var eurekaResponse = response.readEntity(KiwiGenericTypes.MAP_OF_STRING_TO_OBJECT_GENERIC_TYPE);
                var apps = EurekaResponseParser.parseEurekaApplicationsResponse(eurekaResponse);

                return apps.size() > 0;
            }

            return false;
        });
    }

    public static void clearAllInstances(String baseUrl) {
        var client = ClientBuilder.newClient().register(GZipEncoder.class);

        var instances = await().atMost(1, MINUTES).until(() -> findAllInstances(baseUrl), all -> isNotNullOrEmpty(all));

        LOG.info("Found {} instances to delete", instances.size());
        instances.forEach(instance -> {
            var unregisterResponse = client.target(baseUrl)
                    .path("apps/{appId}/{instanceId}")
                    .resolveTemplate("appId", instance.getApp())
                    .resolveTemplate("instanceId", instance.getInstanceId())
                    .request()
                    .accept(APPLICATION_JSON_TYPE)
                    .delete();
            LOG.info("Response deleting instance {}/{}: {}", instance.getApp(), instance.getInstanceId(), unregisterResponse.getStatus());
        });
    }

    private static List<EurekaInstance> findAllInstances(String baseUrl) {
        var client = ClientBuilder.newClient().register(GZipEncoder.class);

        var response = client.target(baseUrl)
                .path("apps")
                .request()
                .accept(APPLICATION_JSON_TYPE)
                .get();

        if (successful(response)) {
            LOG.info("Successful response finding all instances");
            var eurekaResponse = response.readEntity(KiwiGenericTypes.MAP_OF_STRING_TO_OBJECT_GENERIC_TYPE);
            return EurekaResponseParser.parseEurekaApplicationsResponse(eurekaResponse);
        } else {
            LOG.warn("Call to retrieve all instances returned a non-success code: {}", response.getStatus());
        }

        return List.of();
    }

    public static void assertApplicationIsRegistered(String appId, String baseUrl) {
        assertOkResponse(findApplication(appId, baseUrl));
    }

    public static void assertApplicationIsNotRegistered(String appId, String baseUrl) {
        assertNotFoundResponse(findApplication(appId, baseUrl));
    }

    private static Response findApplication(String appId, String baseUrl) {
        var client = ClientBuilder.newClient().register(GZipEncoder.class);

        return client.target(baseUrl)
                .path("apps/{appId}")
                .resolveTemplate("appId", appId)
                .request()
                .accept(APPLICATION_JSON_TYPE)
                .get();
    }

}
