package com.lumintra.backend.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lumintra Backend API")
                        .version("1.0.0")
                        .description("REST API documentation for Lumintra LED Car Decoration Platform")
                        .contact(new Contact()
                                .name("Lumintra Engineering Team")
                                .email("noreply@lumintra.shop")));
    }
}
