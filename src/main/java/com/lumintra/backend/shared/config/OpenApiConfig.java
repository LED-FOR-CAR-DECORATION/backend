package com.lumintra.backend.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Lumintra Backend API").version("v1")
                        .description("API Lumintra. Đăng ký, đăng nhập, Google login và refresh không yêu cầu access token. "
                                + "Dùng accessToken nhận từ đăng nhập trong Authorize để thử API được bảo vệ."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Nhập accessToken, không thêm tiền tố Bearer.")));
    }
}
