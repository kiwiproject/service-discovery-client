package org.kiwiproject.registry.util;

import lombok.experimental.UtilityClass;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.model.Port;

import java.util.List;

@UtilityClass
public class ServiceInfoHelper {

    public static ServiceInfo buildTestServiceInfo() {
        return new ServiceInfo() {

            @Override
            public String getName() {
                return "test-service";
            }

            @Override
            public String getHostname() {
                return "localhost";
            }

            @Override
            public String getIp() {
                return "127.0.0.1";
            }

            @Override
            public List<Port> getPorts() {
                return List.of(
                        Port.builder()
                                .number(80)
                                .secure(Port.Security.NOT_SECURE)
                                .type(Port.PortType.APPLICATION)
                                .build()
                );
            }

            @Override
            public String getVersion() {
                return "0.1.0";
            }
        };
    }
}
