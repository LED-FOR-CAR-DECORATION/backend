package com.lumintra.backend.iam.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record GoogleLoginRequest(
        @NotBlank @Size(max = 16384)
        @Schema(description = "Google ID token nhận từ Google Identity Services; không phải access token.", requiredMode = Schema.RequiredMode.REQUIRED, format = "password") String idToken,
        @Size(max = 72)
        @Schema(description = "Chỉ cần khi liên kết tài khoản hiện có qua email mà Google không quản lý (không phải Gmail/Workspace).", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String password
) {
    @AssertTrue(message = "Mật khẩu không được vượt quá 72 byte UTF-8.")
    @Schema(hidden = true)
    public boolean isPasswordWithinLimit() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
