package com.webapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for OpenAPI/Swagger documentation
 */
@Configuration
public class SwaggerConfig {

    @Value("${springdoc.server.url:http://localhost:8080}")
    private String serverUrl;

    /**
     * Configure OpenAPI documentation for the API
     */
    @Bean
    public OpenAPI serverlessOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Autodeployr API")
                        .description("API for automatic application transformation to serverless functions")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Autodeployr Team")
                                .email("zaramihnea@icloud.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(new Server().url(serverUrl).description("API Server")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT token")
                        ))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}