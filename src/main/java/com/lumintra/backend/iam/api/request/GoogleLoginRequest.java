package com.lumintra.backend.iam.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record GoogleLoginRequest(
        @NotBlank @Size(max = 16384)
        @Schema(description = "Google ID token returned by Google Identity Services, not a Google access token.", requiredMode = Schema.RequiredMode.REQUIRED, format = "password") String idToken,
        @Size(max = 72)
        @Schema(description = "Required only when linking an existing account whose email is not managed by Google (not Gmail/Workspace).", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String password
) {
    @AssertTrue(message = "The password must not exceed 72 UTF-8 bytes.")
    @Schema(hidden = true)
    public boolean isPasswordWithinLimit() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
