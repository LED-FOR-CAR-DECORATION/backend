package com.lumintra.backend.iam.api.response;

import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Builder
public class LoginResponse {
    @Schema(description = "JWT used as a Bearer token for protected APIs.")
    private String accessToken;
    @Schema(description = "Refresh token valid for 30 days.")
    private String refreshToken;
    @Schema(example = "Bearer")
    private String tokenType;
    @Schema(description = "Access token lifetime in seconds.", example = "900")
    private long expiresIn; // 900 seconds (15 minutes)
}
