package com.carddemo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The startup guard that keeps a missing secret from surfacing as a confusing authentication
 * failure further into the boot sequence.
 */
class RequiredSecretsCheckTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef0123456789";

    private final RequiredSecretsCheck check = new RequiredSecretsCheck();

    @Test
    @DisplayName("Both secrets present and long enough: startup continues")
    void acceptsCompleteConfiguration() {
        assertDoesNotThrow(() -> check.onApplicationEvent(event(environment("secret", VALID_SECRET))));
    }

    @Test
    @DisplayName("Missing database password is named explicitly")
    void rejectsMissingDatabasePassword() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> check.onApplicationEvent(event(environment(null, VALID_SECRET))));
        assertTrue(failure.getMessage().contains("DB_PASSWORD is not set"), failure.getMessage());
        assertTrue(failure.getMessage().contains(".env.example"), "the message points at the fix");
    }

    @Test
    @DisplayName("Missing signing key is named explicitly")
    void rejectsMissingSigningKey() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> check.onApplicationEvent(event(environment("secret", null))));
        assertTrue(failure.getMessage().contains("CARDDEMO_JWT_SECRET is not set"), failure.getMessage());
    }

    @Test
    @DisplayName("A signing key shorter than 32 characters is rejected with its length")
    void rejectsShortSigningKey() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> check.onApplicationEvent(event(environment("secret", "too-short"))));
        assertTrue(failure.getMessage().contains("only 9 characters"), failure.getMessage());
    }

    @Test
    @DisplayName("A blank value counts as missing")
    void treatsBlankAsMissing() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> check.onApplicationEvent(event(environment("   ", VALID_SECRET))));
        assertTrue(failure.getMessage().contains("DB_PASSWORD is not set"), failure.getMessage());
    }

    @Test
    @DisplayName("Every problem is reported at once, not one per restart")
    void reportsAllProblemsTogether() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> check.onApplicationEvent(event(environment(null, null))));
        assertTrue(failure.getMessage().contains("DB_PASSWORD is not set"), failure.getMessage());
        assertTrue(failure.getMessage().contains("CARDDEMO_JWT_SECRET is not set"), failure.getMessage());
    }

    private static ConfigurableEnvironment environment(String dbPassword, String jwtSecret) {
        MockEnvironment environment = new MockEnvironment();
        if (dbPassword != null) {
            environment.setProperty("DB_PASSWORD", dbPassword);
        }
        if (jwtSecret != null) {
            environment.setProperty("CARDDEMO_JWT_SECRET", jwtSecret);
        }
        return environment;
    }

    private static ApplicationEnvironmentPreparedEvent event(ConfigurableEnvironment environment) {
        return new ApplicationEnvironmentPreparedEvent(
                new org.springframework.boot.DefaultBootstrapContext(),
                new SpringApplication(),
                new String[0],
                environment);
    }
}
