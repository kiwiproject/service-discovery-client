package org.kiwiproject.registry.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;

@DisplayName("Port")
@ExtendWith(SoftAssertionsExtension.class)
class PortTest {

    @Nested
    class FactoryMethod {

        @ParameterizedTest
        @CsvSource({
                "0, APPLICATION, SECURE",
                "0, ADMIN, SECURE",
                "8080, APPLICATION, NOT_SECURE",
                "8081, ADMIN, NOT_SECURE",
                "42000, APPLICATION, SECURE",
                "42001, ADMIN, SECURE",
                "65353, APPLICATION, SECURE",
                "65353, ADMIN, SECURE",
        })
        void shouldCreatePort(int number, PortType portType, Security security, SoftAssertions softly) {
            var port = Port.of(number, portType, security);

            softly.assertThat(port.getNumber()).isEqualTo(number);
            softly.assertThat(port.getType()).isEqualTo(portType);
            softly.assertThat(port.getSecure()).isEqualTo(security);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1024, -1, 65_536})
        void shouldRejectInvalidPortNumbers(int number) {
            assertThatIllegalStateException()
                    .isThrownBy(() -> Port.of(number, PortType.APPLICATION, Security.SECURE));
        }

        @Test
        void shouldSetDefaultValueForPortType() {
            var port = Port.of(8080, null, Security.SECURE);
            assertThat(port.getType()).isEqualTo(PortType.APPLICATION);
        }

        @Test
        void shouldSetDefaultValueForSecurity() {
            var port = Port.of(9001, PortType.APPLICATION, null);
            assertThat(port.getSecure()).isEqualTo(Security.SECURE);
        }
    }

}