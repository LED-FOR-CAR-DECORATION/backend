package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.*;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.application.*;
import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_FAILED / INVALID_REQUEST", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "INVALID_TOKEN / INVALID_GOOGLE_TOKEN", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ACCOUNT_DISABLED / GOOGLE_LINK_REQUIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "DATA_CONFLICT: request đồng thời xung đột email hoặc Google identity; thử đăng nhập lại", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "ACCOUNT_LOCKED: tài khoản bị khóa đăng nhập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "GOOGLE_UNAVAILABLE / AUTH_UNAVAILABLE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class SessionAuthController {
    private final GoogleLoginUseCase google;
    private final TokenSessionService sessions;
    private final LogoutUseCase logout;

    @PostMapping("/google")
    @Operation(summary = "Đăng nhập Google", description = "Xác minh Google ID token. Liên kết email đã có hoặc tạo Customer mới. Email ngoài Gmail/Workspace cần mật khẩu tài khoản hiện có để liên kết.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cặp JWT hệ thống", useReturnTypeSchema = true)
    public ResponseEntity<ApiResponse<LoginResponse>> google(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(google.execute(request, false)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới cặp token", description = "Không cần access token. Refresh token phải còn hạn, chưa bị thu hồi và là token mới nhất của phiên. Rotation không gia hạn mốc 30 ngày ban đầu; token cũ bị vô hiệu ngay.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cặp JWT mới", useReturnTypeSchema = true)
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(sessions.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Đăng xuất mọi thiết bị", description = "Thu hồi tất cả phiên/refresh token trong database; blacklist access token hiện tại trên Redis đến lúc hết hạn. Database bảo đảm thu hồi ngay cả khi Redis gián đoạn.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Đã thu hồi mọi phiên", content = @Content)
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        logout.execute(authorization.substring(7));
        return ResponseEntity.noContent().build();
    }
}
