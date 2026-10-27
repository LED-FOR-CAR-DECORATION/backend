package com.lumintra.backend.iam.api.response;

import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Builder
public class LoginResponse {
    @Schema(description = "JWT dùng làm Bearer token cho API được bảo vệ.")
    private String accessToken;
    @Schema(description = "Refresh token có thời hạn 30 ngày.")
    private String refreshToken;
    @Schema(example = "Bearer")
    private String tokenType;
    @Schema(description = "Thời hạn access token, tính bằng giây.", example = "900")
    private long expiresIn; // 900 seconds (15 minutes)
}
