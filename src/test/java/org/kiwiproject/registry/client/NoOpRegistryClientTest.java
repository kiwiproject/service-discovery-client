package org.kiwiproject.registry.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.text.RandomStringGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kiwiproject.registry.client.RegistryClient.InstanceQuery;

import java.util.stream.Stream;

@DisplayName("NoOpRegistryClient")
class NoOpRegistryClientTest {

    private static RandomStringGenerator generator;

    private NoOpRegistryClient registryClient;

    @BeforeAll
    static void beforeAll() {
        generator = new RandomStringGenerator.Builder()
                .withinRange('a', 'z')
                .build();
    }

    @BeforeEach
    void setUp() {
        registryClient = new NoOpRegistryClient();
    }

    @RepeatedTest(10)
    void findAllServiceInstanceBy_shouldReturnEmptyOptional() {
        var serviceName = randomServiceName();
        var instanceId = randomInstanceId();
        assertThat(registryClient.findServiceInstanceBy(serviceName, instanceId)).isEmpty();
    }

    private static String randomInstanceId() {
        return generator.generate(10);
    }

    @ParameterizedTest
    @MethodSource("instanceQueries")
    void findServiceInstanceBy_shouldReturnEmptyList(InstanceQuery query) {
        assertThat(registryClient.findAllServiceInstancesBy(query)).isUnmodifiable().isEmpty();
    }

    static Stream<InstanceQuery> instanceQueries() {
        return Stream.of(
            InstanceQuery.builder().build(),
            InstanceQuery.builder().serviceName(randomServiceName()).build(),
            InstanceQuery.builder().serviceName(randomServiceName()).preferredVersion("2.0.0").build(),
            InstanceQuery.builder().serviceName(randomServiceName()).minimumVersion("1.9.0").preferredVersion("2.0.0").build()
        );
    }

    private static String randomServiceName() {
        var baseName = generator.generate(5);
        return baseName + "-service";
    }

    @RepeatedTest(5)
    void retrieveAllRegisteredInstances_shouldReturnEmptyList() {
        assertThat(registryClient.retrieveAllRegisteredInstances()).isUnmodifiable().isEmpty();
    }
}
