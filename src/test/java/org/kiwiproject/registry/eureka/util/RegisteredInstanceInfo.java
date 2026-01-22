package org.kiwiproject.registry.eureka.util;

public record RegisteredInstanceInfo(
        String appId,
        String instanceId,
        String vipAddress) {
}
