package org.kiwiproject.registry.server;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.model.ServiceInstance;

import java.util.UUID;

/**
 * A No-op implementation of the {@link RegistryService}. This is useful if you want to run a service that needs to register
 * with a registry but don't have a registry service to connect to.
 */
@Slf4j
public class NoopRegistryService implements RegistryService {

    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PACKAGE)
    private ServiceInstance dummyInstance;

    @Override
    public ServiceInstance createCandidateFrom(ServiceInfo serviceInfo) {
        LOG.info("Create dummy service [{}] candidate with service name: {}", serviceInfo.humanReadableName(), serviceInfo.getName());
        return ServiceInstance.builder()
                .instanceId(UUID.randomUUID().toString())
                .serviceName(serviceInfo.getName())
                .hostName(serviceInfo.getHostname())
                .ip(serviceInfo.getIp())
                .status(ServiceInstance.Status.STARTING)
                .commitRef(serviceInfo.getCommitRef())
                .description(serviceInfo.getDescription())
                .ports(serviceInfo.getPorts())
                .version(serviceInfo.getVersion())
                .paths(serviceInfo.getPaths())
                .build();
    }

    @Override
    public ServiceInstance register(ServiceInstance serviceToRegister) {
        if (isNull(dummyInstance)) {
            dummyInstance = serviceToRegister.toBuilder().status(ServiceInstance.Status.UP).build();
        }

        return dummyInstance;
    }

    @Override
    public ServiceInstance updateStatus(ServiceInstance.Status newStatus) {
        checkState(nonNull(dummyInstance), "Can not update status before calling register");

        dummyInstance = dummyInstance.withStatus(newStatus);
        return dummyInstance;
    }

    @Override
    public void unregister() {
        LOG.info("Un-registering dummy service: service [{}], instance [{}]", dummyInstance.getServiceName(), dummyInstance.getInstanceId());
        dummyInstance = null;
    }
}
