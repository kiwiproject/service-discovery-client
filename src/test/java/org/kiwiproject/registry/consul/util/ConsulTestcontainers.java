package org.kiwiproject.registry.consul.util;

import com.google.common.net.HostAndPort;
import lombok.experimental.UtilityClass;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.DockerImageName;

@UtilityClass
public class ConsulTestcontainers {

    private static final int CONSUL_HTTP_PORT = 8500;

    public static ConsulContainer newConsulContainer() {
        return new ConsulContainer(consulDockerImageName());
    }

    public static DockerImageName consulDockerImageName() {
        return DockerImageName.parse("hashicorp/consul:1.15");
    }

    public static HostAndPort consulHostAndPort(ConsulContainer consul) {
        return HostAndPort.fromParts(consul.getHost(), consul.getMappedPort(CONSUL_HTTP_PORT));
    }
}
