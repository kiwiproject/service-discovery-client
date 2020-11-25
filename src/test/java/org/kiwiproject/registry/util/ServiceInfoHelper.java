package org.kiwiproject.registry.util;

import static com.google.common.collect.Lists.newArrayList;

import lombok.experimental.UtilityClass;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;
import org.kiwiproject.registry.model.ServicePaths;

import java.util.List;

@UtilityClass
public class ServiceInfoHelper {

    public static ServiceInfo buildTestServiceinfoWithHostName(String hostname) {
        return buildTestServiceInfo("test-service", hostname);
    }

    public static ServiceInfo buildTestServiceInfoWithName(String name) {
        return buildTestServiceInfo(name, "localhost");
    }

    public static ServiceInfo buildTestServiceInfo() {
        return buildTestServiceInfo("test-service", "localhost");
    }

    public static ServiceInfo buildTestServiceInfo(String name, String hostname) {
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
        };
    }
}
