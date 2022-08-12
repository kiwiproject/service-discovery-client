package org.kiwiproject.registry.eureka.server;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.registry.eureka.server.EurekaRegistryService.APP_TIMESTAMP_FORMATTER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.registry.eureka.common.EurekaInstance;
import org.kiwiproject.registry.eureka.common.EurekaRestClient;
import org.kiwiproject.registry.eureka.config.EurekaRegistrationConfig;
import org.kiwiproject.registry.exception.RegistrationException;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.model.ServicePaths;
import org.kiwiproject.retry.SimpleRetryer;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.Response;

/**
 * Unlike the {@link EurekaRegistryServiceIntegrationTest}, this set of tests will mock out the actual calls to Eureka
 * so that we can test edge cases and error handling.
 */
@DisplayName("EurekaRegistryService")
class EurekaRegistryServiceTest {

    private EurekaRestClient eurekaRestClient;
    private KiwiEnvironment kiwiEnvironment;
    private EurekaRegistrationConfig config;

    private EurekaRegistryService eurekaRegistryService;

    @BeforeEach
    void setUp() {
        eurekaRestClient = mock(EurekaRestClient.class);
        kiwiEnvironment = mock(KiwiEnvironment.class);
        config = new EurekaRegistrationConfig();
        config.setRegistryUrls("https://localhost:8761/eureka/v2");

        var retryer = SimpleRetryer.builder()
                .environment(kiwiEnvironment)
                .maxAttempts(5)
                .retryDelayTime(200)
                .retryDelayUnit(TimeUnit.MILLISECONDS)
                .build();

        eurekaRegistryService = new EurekaRegistryService(config, eurekaRestClient, kiwiEnvironment, retryer, retryer, retryer, retryer);
    }

    @Nested
    class Register {
        @Test
        void shouldThrowIllegalStateWhenAlreadyRegistered() {
            eurekaRegistryService.registeredInstance.set(EurekaInstance.builder().build());

            var instance = ServiceInstance.builder().build();
            assertThatThrownBy(() -> eurekaRegistryService.register(instance))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("Cannot register. Already managing a registered instance: ");
        }

        @Test
        void shouldThrowRegistrationExceptionWhenRetriesRunOutDueToNonSuccessResponse() {
            var now = Instant.now();
            when(kiwiEnvironment.currentInstant()).thenReturn(now);

            var appId = f("test-service-{}", APP_TIMESTAMP_FORMATTER.format(now)).toUpperCase(Locale.getDefault());
            when(eurekaRestClient.register(eq(config.getRegistryUrls()), eq(appId), any(EurekaInstance.class)))
                    .thenReturn(Response.serverError().build());

            var instance = ServiceInstance.builder()
                    .serviceName("test-service")
                    .status(ServiceInstance.Status.UP)
                    .ports(List.of(Port.of(8080, Port.PortType.APPLICATION, Port.Security.SECURE)))
                    .paths(ServicePaths.builder().build())
                    .description("The best service")
                    .version("42.0.1")
                    .commitRef("abcdef")
                    .build();

            assertThatThrownBy(() -> eurekaRegistryService.register(instance))
                    .isInstanceOf(RegistrationException.class)
                    .hasMessageContaining("Received errors or non-204 responses on ALL")
                    .hasMessageContaining("attempts to register (via POST) with Eureka");
        }

        @Test
        void shouldThrowRegistrationExceptionWhenRetriesRunOutDueToException() {
            var now = Instant.now();
            when(kiwiEnvironment.currentInstant()).thenReturn(now);

            var appId = f("test-service-{}", APP_TIMESTAMP_FORMATTER.format(now)).toUpperCase(Locale.getDefault());
            when(eurekaRestClient.register(eq(config.getRegistryUrls()), eq(appId), any(EurekaInstance.class)))
                    .thenThrow(new RuntimeException("oops"));

            var instance = ServiceInstance.builder()
                    .serviceName("test-service")
                    .status(ServiceInstance.Status.UP)
                    .ports(List.of(Port.of(8080, Port.PortType.APPLICATION, Port.Security.SECURE)))
                    .paths(ServicePaths.builder().build())
                    .description("The best service")
                    .version("42.0.1")
                    .commitRef("abcdef")
                    .build();

            assertThatThrownBy(() -> eurekaRegistryService.register(instance))
                    .isInstanceOf(RegistrationException.class)
                    .hasMessageContaining("Received errors or non-204 responses on ALL")
                    .hasMessageContaining("attempts to register (via POST) with Eureka");
        }

        @Test
        void shouldThrowRegistrationExceptionWhenRetriesRunOutWaitingForRegistrationDueToNonSuccessResponse() {
            var now = Instant.now();
            when(kiwiEnvironment.currentInstant()).thenReturn(now);

            var appId = f("test-service-{}", APP_TIMESTAMP_FORMATTER.format(now)).toUpperCase(Locale.getDefault());
            when(eurekaRestClient.register(eq(config.getRegistryUrls()), eq(appId), any(EurekaInstance.class)))
                    .thenReturn(Response.noContent().build());

            when(eurekaRestClient.findInstance(config.getRegistryUrls(), appId, "localhost"))
                    .thenReturn(Response.serverError().build());

            var instance = ServiceInstance.builder()
                    .serviceName("test-service")
                    .status(ServiceInstance.Status.UP)
                    .ports(List.of(Port.of(8080, Port.PortType.APPLICATION, Port.Security.SECURE)))
                    .paths(ServicePaths.builder().build())
                    .description("The best service")
                    .version("42.0.1")
                    .commitRef("abcdef")
                    .build();

            assertThatThrownBy(() -> eurekaRegistryService.register(instance))
                    .isInstanceOf(RegistrationException.class)
                    .hasMessageContaining("Received errors or non-204 responses on ALL")
                    .hasMessageContaining("attempts to register (via POST) with Eureka");
        }
    }
}
