package org.kiwiproject.registry.exception;

import static org.kiwiproject.registry.test.util.StandardExceptionTests.standardConstructorTestsFor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;

@DisplayName("RegistrationException")
class RegistrationExceptionTest {

    @TestFactory
    Collection<DynamicTest> shouldHaveStandardConstructors() {
        return standardConstructorTestsFor(RegistrationException.class);
    }
}
