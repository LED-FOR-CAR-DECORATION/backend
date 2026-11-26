package com.lumintra.backend.iam.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "JWT authentication response containing access and refresh tokens")
@Getter
@Builder
public class LoginResponse {

    @Schema(description = "Signed JWT access token for authenticating protected requests", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "Signed JWT refresh token for renewing access tokens", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;

    @Schema(description = "OAuth token authorization scheme type", example = "Bearer")
    private String tokenType;

    @Schema(description = "Access token lifespan in seconds (15 minutes = 900s)", example = "900")
    private long expiresIn;

    public static LoginResponse of(String accessToken, String refreshToken) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900) // 15 minutes in seconds
                .build();
    }
}
