package com.carddemo;

import com.carddemo.config.RequiredSecretsCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Entry point for the CardDemo Spring Boot backend.
 *
 * <p>This service is the Java replacement for the CICS/VSAM online estate and the z/OS batch
 * estate of the AWS Mainframe Modernization CardDemo COBOL application. Every business rule
 * implemented here traces back to a COBOL program or copybook under {@code Cobol_Code}.</p>
 */
// Authentication is the CardDemo user store plus a signed token, so the default in-memory user of
// Spring Boot's security auto-configuration is excluded.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableTransactionManagement
public class CardDemoApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(CardDemoApplication.class);
        // Checks DB_PASSWORD and CARDDEMO_JWT_SECRET before the data source or any bean is created,
        // so a missing secret reports itself rather than surfacing as an authentication failure.
        application.addListeners(new RequiredSecretsCheck());
        application.run(args);
    }
}
