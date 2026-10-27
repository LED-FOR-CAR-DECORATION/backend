package com.lumintra.backend.iam.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tài khoản Customer vừa được tạo. Đăng nhập riêng để nhận JWT.")
public record RegisterResponse(
        @Schema(example = "123") Long userId,
        @Schema(example = "customer@example.com") String email,
        @Schema(example = "Nguyễn Văn A") String fullName
) {}
