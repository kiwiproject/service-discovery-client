package org.kiwiproject.registry.util;

import static com.google.common.collect.Lists.newArrayList;

import lombok.experimental.UtilityClass;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;
import org.kiwiproject.registry.model.ServicePaths;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class ServiceInfoHelper {

    public static ServiceInfo buildTestServiceinfoWithHostName(String hostname) {
        return buildTestServiceInfo("test-service", hostname);
    }

    public static ServiceInfo buildTestServiceInfoWithName(String name) {
        return buildTestServiceInfo(name, "localhost");
    }

    public static ServiceInfo buildTestServiceInfo() {
        return buildTestServiceInfo(new HashMap<>());
    }

    public static ServiceInfo buildTestServiceInfo(String name, String hostname) {
        return buildTestServiceInfo(name, hostname, new HashMap<>());
    }

    public static ServiceInfo buildTestServiceInfo(Map<String, String> metadata) {
        return buildTestServiceInfo("test-service", "localhost", metadata);
    }

    public static ServiceInfo buildTestServiceInfo(String name, String hostname, Map<String, String> metadata) {
        return new ServiceInfo() {

            private final List<Port> ports = newArrayList(
                    Port.of(80, PortType.APPLICATION, Security.NOT_SECURE)
            );

            private final ServicePaths paths = ServicePaths.builder().build();

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getHostname() {
                return hostname;
            }

            @Override
            public String getIp() {
                return "127.0.0.1";
            }

            @Override
            public List<Port> getPorts() {
                return ports;
            }

            @Override
            public String getVersion() {
                return "0.1.0";
            }

            @Override
            public ServicePaths getPaths() {
                return paths;
            }

            @Override
            public Map<String, String> getMetadata() {
                return metadata;
            }
        };
    }
}
