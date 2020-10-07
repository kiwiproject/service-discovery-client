package org.kiwiproject.registry.client;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.model.ServiceInstance;

import java.util.Collections;
import java.util.List;

@DisplayName("ServiceInstanceFilter")
class ServiceInstanceFilterTest {

    private ServiceInstance instance;

    @Nested
    class VersionIsAtLeast {

        @Test
        void shouldThrowIllegalArgumentException_WhenBadArguments() {
            instance = ServiceInstance.builder().version("2018.11.02-SNAPSHOT").build();

            assertThatThrownBy(() -> ServiceInstanceFilter.versionIsAtLeast(instance, null))
                    .isExactlyInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> ServiceInstanceFilter.versionIsAtLeast(instance, ""))
                    .isExactlyInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> ServiceInstanceFilter.versionIsAtLeast(instance, " "))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldReturnCorrectValues_WhenValidArguments() {
            instance = ServiceInstance.builder().version("2018.11.02-SNAPSHOT").build();

            assertThat(ServiceInstanceFilter.versionIsAtLeast(instance, "2017.11.02-SNAPSHOT")).isTrue();
            assertThat(ServiceInstanceFilter.versionIsAtLeast(instance, "2018.10.01-SNAPSHOT")).isTrue();
            assertThat(ServiceInstanceFilter.versionIsAtLeast(instance, "2018.11.01-SNAPSHOT")).isTrue();
            assertThat(ServiceInstanceFilter.versionIsAtLeast(instance, "2018.11.02-SNAPSHOT")).isTrue();
            assertThat(ServiceInstanceFilter.versionIsAtLeast(instance, "2018.11.03-SNAPSHOT")).isFalse();
            assertThat(ServiceInstanceFilter.versionIsAtLeast(instance, "2018.12.01-SNAPSHOT")).isFalse();
            assertThat(ServiceInstanceFilter.versionIsAtLeast(instance, "2019.01.01-SNAPSHOT")).isFalse();
        }
    }

    @Nested
    class VersionIsExactly {

        @Test
        void shouldThrowIllegalArgumentException_WhenArguments() {
            instance = ServiceInstance.builder().version("2018.11.02-SNAPSHOT").build();

            assertThatThrownBy(() -> ServiceInstanceFilter.versionIsExactly(instance, null))
                    .isExactlyInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> ServiceInstanceFilter.versionIsExactly(instance, ""))
                    .isExactlyInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> ServiceInstanceFilter.versionIsExactly(instance, " "))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldReturnCorrectValues_WhenValidArguments() {
            instance = ServiceInstance.builder().version("2018.11.01-SNAPSHOT").build();

            assertThat(ServiceInstanceFilter.versionIsExactly(instance, "2017.11.02-SNAPSHOT")).isFalse();
            assertThat(ServiceInstanceFilter.versionIsExactly(instance, "2018.10.01-SNAPSHOT")).isFalse();
            assertThat(ServiceInstanceFilter.versionIsExactly(instance, "2018.11.01-SNAPSHOT")).isTrue();
            assertThat(ServiceInstanceFilter.versionIsExactly(instance, "2018.11.02-SNAPSHOT")).isFalse();
            assertThat(ServiceInstanceFilter.versionIsExactly(instance, "2018.11.03-SNAPSHOT")).isFalse();
            assertThat(ServiceInstanceFilter.versionIsExactly(instance, "2018.12.01-SNAPSHOT")).isFalse();
            assertThat(ServiceInstanceFilter.versionIsExactly(instance, "2019.01.01-SNAPSHOT")).isFalse();
            assertThat(ServiceInstanceFilter.versionIsExactly(instance, "2018.11.01-snapshot")).isTrue();
            assertThat(ServiceInstanceFilter.versionIsExactly(instance, "2018.11.01")).isFalse();
        }
    }

    @Nested
    class FindInstancesWithLatestVersion {

        @RepeatedTest(10)
        void shouldFindCorrectInstances() {
            var maxVersion = "2018.12.02-SNAPSHOT";

            var instance1 = ServiceInstance.builder().version("2018.10.01-SNAPSHOT").build();
            var instance2 = ServiceInstance.builder().version("2018.10.01-SNAPSHOT").build();
            var instance3 = ServiceInstance.builder().version("2018.10.02-SNAPSHOT").build();
            var instance4 = ServiceInstance.builder().version("2018.11.01-SNAPSHOT").build();
            var instance5 = ServiceInstance.builder().version("2018.11.01-SNAPSHOT").build();
            var instance6 = ServiceInstance.builder().version("2018.11.02-SNAPSHOT").build();
            var instance7 = ServiceInstance.builder().version("2018.11.02-SNAPSHOT").build();
            var instance8 = ServiceInstance.builder().version("2018.12.01-SNAPSHOT").build();
            var instance9 = ServiceInstance.builder().version(maxVersion).build();
            var instance10 = ServiceInstance.builder().version(maxVersion).build();

            var instances = newArrayList(
                    instance1,
                    instance2,
                    instance3,
                    instance4,
                    instance5,
                    instance6,
                    instance7,
                    instance8,
                    instance9,
                    instance10
            );
            Collections.shuffle(instances);

            var found = ServiceInstanceFilter.findInstancesWithLatestVersion(instances);
            assertThat(found).hasSize(2).extracting(ServiceInstance::getVersion).containsOnly(maxVersion);
        }
    }

    @Nested
    class FilterInstancesByVersion {
        private List<ServiceInstance> instances;

        @BeforeEach
        void setUp() {
            var maxVersion = "2018.12.02-SNAPSHOT";

            var instance1 = ServiceInstance.builder().version("2018.10.01-SNAPSHOT").build();
            var instance2 = ServiceInstance.builder().version("2018.10.01-SNAPSHOT").build();
            var instance3 = ServiceInstance.builder().version("2018.10.02-SNAPSHOT").build();
            var instance4 = ServiceInstance.builder().version("2018.11.01-SNAPSHOT").build();
            var instance5 = ServiceInstance.builder().version("2018.11.01-SNAPSHOT").build();
            var instance6 = ServiceInstance.builder().version("2018.11.02-SNAPSHOT").build();
            var instance7 = ServiceInstance.builder().version("2018.11.02-SNAPSHOT").build();
            var instance8 = ServiceInstance.builder().version("2018.12.01-SNAPSHOT").build();
            var instance9 = ServiceInstance.builder().version(maxVersion).build();
            var instance10 = ServiceInstance.builder().version(maxVersion).build();

            instances = newArrayList(
                    instance1,
                    instance2,
                    instance3,
                    instance4,
                    instance5,
                    instance6,
                    instance7,
                    instance8,
                    instance9,
                    instance10
            );
            Collections.shuffle(instances);
        }

        @Test
        void shouldReturnEmptyListWhenGivenListIsEmpty() {
            assertThat(ServiceInstanceFilter.filterInstancesByVersion(List.of(), 
                    RegistryClient.InstanceQuery.builder().build())).isEmpty();
        }

        @Test
        void shouldReturnCopyOfGivenListWhenNoPredicates() {
            assertThat(ServiceInstanceFilter.filterInstancesByVersion(instances, RegistryClient.InstanceQuery.builder().build()))
                    .isNotSameAs(instances)
                    .containsAll(instances);
        }

        @Test
        void shouldReturnEmptyListWhenNoInstancesMeetMinimumRequirement() {
            assertThat(ServiceInstanceFilter.filterInstancesByVersion(instances,
                    RegistryClient.InstanceQuery.builder().minimumVersion("2019.01.01-SNAPSHOT").build())).isEmpty();
        }

        @Test
        void shouldReturnListOfServicesMeetingMinimumRequirement() {
            assertThat(ServiceInstanceFilter.filterInstancesByVersion(instances,
                    RegistryClient.InstanceQuery.builder().minimumVersion("2018.11.01-SNAPSHOT").build()))
                    .hasSize(7);
        }

        @Test
        void shouldReturnEmptyListWhenNoInstancesMeetPreferredRequirement() {
            assertThat(ServiceInstanceFilter.filterInstancesByVersion(instances,
                    RegistryClient.InstanceQuery.builder().preferredVersion("2019.01.01-SNAPSHOT").build()))
                    .containsAll(ServiceInstanceFilter.findInstancesWithLatestVersion(instances));
        }

        @Test
        void shouldReturnListOfServicesMeetingPreferredRequirement() {
            assertThat(ServiceInstanceFilter.filterInstancesByVersion(instances,
                    RegistryClient.InstanceQuery.builder().preferredVersion("2018.11.01-SNAPSHOT").build()))
                    .hasSize(2);
        }
    }
}
