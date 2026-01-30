package com.example.payment_services.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT token from external authentication server"
)
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
            .title("Payment Microservice API")
            .version("1.0.0")
            .description("Payment processing microservice with Razorpay integration")
            .contact(new Contact()
            .name("API Support")
            .email("support@example.com"))
            .license(new License()
            .name("Apache 2.0")
            .url("http://springdoc.org")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
            .addSecuritySchemes("bearerAuth",
            new io.swagger.v3.oas.models.security.SecurityScheme()
            .type(Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("Enter your JWT token from the authentication server")));
    }
}