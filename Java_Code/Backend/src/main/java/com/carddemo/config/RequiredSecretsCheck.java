package com.carddemo.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails startup with an actionable message when a required secret is missing.
 *
 * <p>{@code application.yml} deliberately gives {@code DB_PASSWORD} and {@code CARDDEMO_JWT_SECRET}
 * no default, so a credential can never be inherited from source control. Without this check the
 * symptoms are misleading: an unset {@code DB_PASSWORD} leaves the placeholder unresolved and
 * PostgreSQL reports {@code password authentication failed}, which reads like a wrong password
 * rather than an absent one.</p>
 *
 * <p>The listener runs on {@link ApplicationEnvironmentPreparedEvent}, before the data source,
 * Flyway or any bean is created, and inspects the source variables rather than the properties that
 * interpolate them.</p>
 */
public class RequiredSecretsCheck implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** Minimum length the HMAC signing key must have; mirrored by {@link JwtService}. */
    static final int MINIMUM_SECRET_LENGTH = 32;

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        List<String> problems = new ArrayList<>();

        if (isBlank(environment.getProperty("DB_PASSWORD"))) {
            problems.add("DB_PASSWORD is not set (PostgreSQL password).");
        }

        String jwtSecret = environment.getProperty("CARDDEMO_JWT_SECRET");
        if (isBlank(jwtSecret)) {
            problems.add("CARDDEMO_JWT_SECRET is not set (session-token signing key).");
        } else if (jwtSecret.length() < MINIMUM_SECRET_LENGTH) {
            problems.add("CARDDEMO_JWT_SECRET is only " + jwtSecret.length()
                    + " characters; at least " + MINIMUM_SECRET_LENGTH + " are required.");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(buildMessage(problems));
        }
    }

    private static String buildMessage(List<String> problems) {
        StringBuilder message = new StringBuilder();
        message.append(System.lineSeparator())
                .append("CardDemo cannot start: required secrets are missing.")
                .append(System.lineSeparator());
        for (String problem : problems) {
            message.append("  - ").append(problem).append(System.lineSeparator());
        }
        message.append(System.lineSeparator())
                .append("Copy Backend/.env.example to Backend/.env and fill it in, then start with")
                .append(System.lineSeparator())
                .append("Java_Code/run-backend.ps1, or export the variables yourself. In a deployment,")
                .append(System.lineSeparator())
                .append("supply them from the platform's secret store.")
                .append(System.lineSeparator());
        return message.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
