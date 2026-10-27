package com.lumintra.backend.iam.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record RefreshTokenRequest(
        @NotBlank @Size(max = 16384)
        @Schema(description = "Refresh token hiện tại; chỉ sử dụng thành công một lần khi rotation.", requiredMode = Schema.RequiredMode.REQUIRED, format = "password") String refreshToken
) {}
