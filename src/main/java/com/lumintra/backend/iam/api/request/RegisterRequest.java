package com.lumintra.backend.iam.api.request;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

@Schema(description = "Đăng ký tài khoản Customer. Email được trim và chuyển chữ thường; mật khẩu giữ nguyên.")
public record RegisterRequest(
        @Schema(description = "Họ tên 2–100 ký tự, không chứa ký tự điều khiển/định dạng.", example = "Nguyễn Văn A", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Vui lòng nhập họ tên.")
        @Size(min = 2, max = 100, message = "Họ tên phải có từ 2 đến 100 ký tự.")
        @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$", message = "Họ tên chứa ký tự không hợp lệ.")
        String fullName,
        @Schema(description = "Email hợp lệ, tối đa 254 ký tự, chưa được đăng ký.", example = "customer@example.com", format = "email", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Vui lòng nhập email.")
        @Email(message = "Email không hợp lệ.")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "Email không hợp lệ.")
        @Size(max = 254, message = "Email không được quá 254 ký tự.")
        String email,
        @Schema(description = "8–72 ký tự, tối đa 72 byte UTF-8; có chữ hoa, chữ thường, số, ký tự đặc biệt; không có khoảng trắng.", example = "Secure123!", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Vui lòng nhập mật khẩu.")
        @Size(min = 8, max = 72, message = "Mật khẩu phải có từ 8 đến 72 ký tự.")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^a-zA-Z0-9\\s])[^\\s]+$",
                message = "Mật khẩu cần chữ hoa, chữ thường, số, ký tự đặc biệt và không chứa khoảng trắng.")
        String password,
        @Schema(description = "Phải trùng khớp với password.", example = "Secure123!", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Vui lòng xác nhận mật khẩu.")
        String confirmPassword
) {
    public RegisterRequest {
        fullName = fullName == null ? null : fullName.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @AssertTrue(message = "Mật khẩu xác nhận không khớp.")
    @Schema(hidden = true)
    public boolean isPasswordsMatch() {
        return Objects.equals(password, confirmPassword);
    }

    @AssertTrue(message = "Mật khẩu không được vượt quá 72 byte UTF-8.")
    @Schema(hidden = true)
    public boolean isPasswordWithinLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
