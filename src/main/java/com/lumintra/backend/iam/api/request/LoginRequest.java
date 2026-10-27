package com.lumintra.backend.iam.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @Schema(description = "Email tài khoản đã đăng ký.", example = "customer@example.com", format = "email", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @Schema(description = "Mật khẩu tài khoản.", example = "Secure123!", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Mật khẩu không được quá 72 ký tự.")
        String password
) {
    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
    }
    @AssertTrue(message = "Mật khẩu không được vượt quá 72 byte UTF-8.")
    @Schema(hidden = true)
    public boolean isPasswordWithinLimit() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
