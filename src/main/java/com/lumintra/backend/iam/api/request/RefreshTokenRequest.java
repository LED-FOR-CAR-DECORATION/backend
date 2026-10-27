package com.lumintra.backend.iam.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record RefreshTokenRequest(
        @NotBlank @Size(max = 16384)
        @Schema(description = "The current refresh token. Each token can be successfully rotated only once.", requiredMode = Schema.RequiredMode.REQUIRED, format = "password") String refreshToken
) {}
