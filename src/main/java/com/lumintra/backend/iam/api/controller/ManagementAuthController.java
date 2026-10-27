package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.*;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.application.*;
import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/auth")
@RequiredArgsConstructor
@Tag(name = "Management Authentication", description = "Chỉ tài khoản Employee có sẵn được đăng nhập; không tự tạo tài khoản nội bộ.")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cặp token cho tài khoản nội bộ", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Sai thông tin đăng nhập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không phải Employee hoặc tài khoản không hoạt động", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Khóa tài khoản 15 phút sau 5 lần sai", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class ManagementAuthController {
    private final LoginUseCase login;

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập email cho ứng dụng quản lý", description = "Cấp access token 15 phút và refresh token 30 ngày. Có lockout, reset bộ đếm và audit cho nhân viên.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(login.execute(request, true)));
    }
}
