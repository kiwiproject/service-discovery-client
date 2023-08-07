package org.kiwiproject.registry.eureka.common;

import static jakarta.ws.rs.client.Entity.json;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.message.GZipEncoder;
import org.kiwiproject.registry.model.ServiceInstance;

import java.util.Map;

@AllArgsConstructor
public class EurekaRestClient {

    @SuppressWarnings("java:S1075")
    private static final String APP_INSTANCE_PATH_TEMPLATE = "/apps/{appId}/{instanceId}";

    private static final String APP_ID = "appId";
    private static final String INSTANCE_ID = "instanceId";
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 1_000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 5_000;

    private final Client client;

    public EurekaRestClient() {
        this(newClient());
    }

    private static Client newClient() {
        return ClientBuilder.newClient()
                .register(GZipEncoder.class)
                .property(ClientProperties.CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT_MILLIS)
                .property(ClientProperties.READ_TIMEOUT, DEFAULT_READ_TIMEOUT_MILLIS);
    }

    public Response register(String eurekaUrl, String appId, EurekaInstance instanceToRegister) {
        return client.target(eurekaUrl)
                .path("/apps/{appId}")
                .resolveTemplate(APP_ID, appId)
                .request()
                .accept(APPLICATION_JSON_TYPE)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .post(json(Map.of("instance", instanceToRegister)));
    }

    public Response findInstance(String eurekaUrl, String appId, String instanceId) {
        return client.target(eurekaUrl)
                .path(APP_INSTANCE_PATH_TEMPLATE)
                .resolveTemplate(APP_ID, appId)
                .resolveTemplate(INSTANCE_ID, instanceId)
                .request()
                .accept(APPLICATION_JSON_TYPE)
                .get();
    }

    public Response findAllInstances(String eurekaUrl) {
        return client.target(eurekaUrl)
                .path("/apps")
                .request()
                .accept(APPLICATION_JSON_TYPE)
                .get();
    }

    public Response findInstancesByVipAddress(String eurekaUrl, String vipAddress) {
        return client.target(eurekaUrl)
                .path("/vips/{vipAddress}")
                .resolveTemplate("vipAddress", vipAddress)
                .request()
                .accept(APPLICATION_JSON_TYPE)
                .get();
    }

    public Response updateStatus(String eurekaUrl, String appId, String instanceId, ServiceInstance.Status newStatus) {
        return client.target(eurekaUrl)
                .path(APP_INSTANCE_PATH_TEMPLATE)
                .path("status")
                .resolveTemplate(APP_ID, appId)
                .resolveTemplate(INSTANCE_ID, instanceId)
                .queryParam("value", newStatus.name())
                .request()
                .accept(APPLICATION_JSON_TYPE)
                .put(json("{}"));
    }

    public Response unregister(String eurekaUrl, String appId, String instanceId) {
        return client.target(eurekaUrl)
                .path(APP_INSTANCE_PATH_TEMPLATE)
                .resolveTemplate(APP_ID, appId)
                .resolveTemplate(INSTANCE_ID, instanceId)
                .request()
                .accept(APPLICATION_JSON_TYPE)
                .delete();
    }

    public Response sendHeartbeat(String eurekaUrl, String appId, String instanceId) {
        return client.target(eurekaUrl)
                .path(APP_INSTANCE_PATH_TEMPLATE)
                .resolveTemplate(APP_ID, appId)
                .resolveTemplate(INSTANCE_ID, instanceId)
                .request()
                .accept(APPLICATION_JSON_TYPE)
                .put(json("{}"));
    }
}
