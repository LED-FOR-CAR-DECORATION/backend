package com.lumintra.backend.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lumintra Backend API")
                        .version("1.0.0")
                        .description("""
                                REST API documentation for Lumintra LED Car Decoration Platform.

                                ---
                                ### Real-Time Commission Chat (WebSocket + STOMP)
                                Swagger UI only executes HTTP requests, so real-time chat events are documented here:
                                - **Handshake Endpoint:** `ws://<host>/ws` (or `wss://<host>/ws` in staging/prod)
                                - **STOMP `CONNECT` Header:** `Authorization: Bearer <accessToken>`
                                - **Subscribe Topic:** `/topic/commissions/{commissionId}` (allowed for owning `CUSTOMER` or `STAFF`/`MANAGER`/`ADMIN`)
                                - **Inbound Frame Payload:** Raw `CommissionMessageResponse` JSON (not wrapped in `ApiResponse`)
                                - **Sending Messages:** Use `POST /api/commissions/{id}/messages` (direct client `SEND` to `/topic/**` is blocked)
                                """)
                        .contact(new Contact()
                                .name("Lumintra Engineering Team")
                                .email("noreply@lumintra.shop")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Authorization header using the Bearer scheme. Enter your token in the text input below.")));
    }
}