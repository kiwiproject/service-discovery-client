package org.kiwiproject.registry.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RegistryClient")
class RegistryClientTest {

    @Nested
    class InstanceQuery {

        @Nested
        class HasNoVersionPredicates {

            @Test
            void shouldReturnTrueWhenNoMinimumOrPreferredVersionsSet() {
                var query = RegistryClient.InstanceQuery.builder().build();

                assertThat(query.hasNoVersionPredicates()).isTrue();
            }

            @Test
            void shouldReturnFalseWhenNoMinimumVersionSet() {
                var query = RegistryClient.InstanceQuery.builder().preferredVersion("0.1.0").build();

                assertThat(query.hasNoVersionPredicates()).isFalse();
            }

            @Test
            void shouldReturnFalseWhenNoPreferredVersionSet() {
                var query = RegistryClient.InstanceQuery.builder().minimumVersion("0.1.0").build();

                assertThat(query.hasNoVersionPredicates()).isFalse();
            }

            @Test
            void shouldReturnFalseWhenMinimumAndPreferredVersionsSet() {
                var query = RegistryClient.InstanceQuery.builder().minimumVersion("0.1.0").preferredVersion("0.1.0").build();

                assertThat(query.hasNoVersionPredicates()).isFalse();
            }
        }

        @Nested
        class HasNoMinimumVersion {
            @Test
            void shouldReturnTrueWhenNoMinimumVersionSet() {
                var query = RegistryClient.InstanceQuery.builder().build();

                assertThat(query.hasNoMinimumVersion()).isTrue();
            }

            @Test
            void shouldReturnFalseWhenMinimumVersionSet() {
                var query = RegistryClient.InstanceQuery.builder().minimumVersion("0.1.0").build();

                assertThat(query.hasNoMinimumVersion()).isFalse();
            }
        }

        @Nested
        class HasNoPreferredVersion {
            @Test
            void shouldReturnTrueWhenNoPreferredVersionSet() {
                var query = RegistryClient.InstanceQuery.builder().build();

                assertThat(query.hasNoPreferredVersion()).isTrue();
            }

            @Test
            void shouldReturnFalseWhenPreferredVersionSet() {
                var query = RegistryClient.InstanceQuery.builder().preferredVersion("0.1.0").build();

                assertThat(query.hasNoPreferredVersion()).isFalse();
            }
        }

        @Test
        void shouldReturnValuesSetInBuilder() {
            var query = RegistryClient.InstanceQuery.builder()
                    .serviceName("test-service")
                    .minimumVersion("0.1.0")
                    .preferredVersion("0.1.0")
                    .build();

            assertThat(query.getServiceName()).isEqualTo("test-service");
            assertThat(query.getMinimumVersion()).isEqualTo("0.1.0");
            assertThat(query.getPreferredVersion()).isEqualTo("0.1.0");
        }
    }
}
