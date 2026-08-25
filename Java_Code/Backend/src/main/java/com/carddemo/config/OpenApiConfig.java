package com.carddemo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Publishes the REST contract at {@code /swagger-ui.html}. */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI cardDemoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CardDemo API")
                        .version("1.0.0")
                        .description("""
                                REST surface of the Java conversion of the AWS Mainframe Modernization
                                CardDemo COBOL application. Endpoints map one to one onto the CICS
                                transactions (CC00 sign-on, CM00/CA00 menus, CAVW/CAUP accounts,
                                CCLI/CCDL/CCUP cards, CT00/CT01/CT02 transactions, CR00 reports,
                                CB00 bill payment, CU00-CU03 user administration) and onto the batch
                                jobs (POSTTRAN, INTCALC, TRANREPT, CREASTMT, COMBTRAN)."""))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
