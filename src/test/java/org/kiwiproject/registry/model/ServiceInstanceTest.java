package org.kiwiproject.registry.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.util.ServiceInfoHelper;

@DisplayName("ServiceInstance")
class ServiceInstanceTest {

    @Test
    void shouldBeAbleToBeCreatedFromServiceInfo() {
        var serviceInfo = ServiceInfoHelper.buildTestServiceInfo();

        var instance = ServiceInstance.fromServiceInfo(serviceInfo);

        assertThat(instance.getInstanceId()).isBlank();
        assertThat(instance.getServiceName()).isEqualTo(serviceInfo.getName());
        assertThat(instance.getHostName()).isEqualTo(serviceInfo.getHostname());
        assertThat(instance.getIp()).isEqualTo(serviceInfo.getIp());
        assertThat(instance.getPorts()).isEqualTo(serviceInfo.getPorts());
        assertThat(instance.getPaths()).isEqualTo(serviceInfo.getPaths());
        assertThat(instance.getStatus()).isNull();
        assertThat(instance.getCommitRef()).isEqualTo(serviceInfo.getCommitRef());
        assertThat(instance.getDescription()).isEqualTo(serviceInfo.getDescription());
        assertThat(instance.getVersion()).isEqualTo(serviceInfo.getVersion());
    }

    @Test
    void shouldAllowSettingCertainFieldsWithWithers() {
        var instance = ServiceInstance.builder()
                .build()
                .withInstanceId("instance")
                .withStatus(ServiceInstance.Status.DOWN);

        assertThat(instance.getInstanceId()).isEqualTo("instance");
        assertThat(instance.getStatus()).isEqualTo(ServiceInstance.Status.DOWN);


    }
}
