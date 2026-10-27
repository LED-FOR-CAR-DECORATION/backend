package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.application.LoginUseCase;
import com.lumintra.backend.iam.application.RegisterUseCase;
import com.lumintra.backend.iam.api.request.RegisterRequest;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import org.springframework.http.HttpStatus;
import com.lumintra.backend.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lumintra.backend.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Đăng ký tài khoản Customer và đăng nhập bằng email/mật khẩu.")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RegisterUseCase registerUseCase;

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản Customer", description = "API công khai. Tạo tài khoản ACTIVE và hồ sơ trong một transaction. "
            + "Không cho phép tự chọn role, không tự đăng nhập và chưa gửi email xác minh.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo tài khoản thành công", useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"data":{"userId":123,"email":"customer@example.com","fullName":"Nguyễn Văn A"}}
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validate thất bại hoặc JSON không hợp lệ",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "Validation", value = """
                                    {"code":"VALIDATION_FAILED","message":"Vui lòng kiểm tra thông tin đã nhập.","status":400,"timestamp":"2026-10-27T00:00:00Z","traceId":"example-trace-id","details":[{"field":"confirmPassword","message":"Mật khẩu xác nhận không khớp."}]}
                                    """),
                            @ExampleObject(name = "InvalidJson", value = """
                                    {"code":"INVALID_REQUEST","message":"Dữ liệu yêu cầu không hợp lệ.","status":400,"timestamp":"2026-10-27T00:00:00Z","traceId":"example-trace-id","details":[]}
                                    """) })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email đã đăng ký hoặc xung đột ràng buộc dữ liệu",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "DuplicateEmail", value = """
                                    {"code":"EMAIL_ALREADY_REGISTERED","message":"Email này đã được đăng ký.","status":409,"timestamp":"2026-10-27T00:00:00Z","traceId":"example-trace-id","details":[{"field":"email","message":"Email này đã được đăng ký."}]}
                                    """),
                            @ExampleObject(name = "ConcurrentConflict", value = """
                                    {"code":"DATA_CONFLICT","message":"Dữ liệu đã tồn tại hoặc không còn hợp lệ. Vui lòng kiểm tra lại.","status":409,"timestamp":"2026-10-27T00:00:00Z","traceId":"example-trace-id","details":[]}
                                    """) })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Lỗi hệ thống", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(registerUseCase.execute(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập", description = "API công khai. Trả access token có thời hạn 900 giây và refresh token. "
            + "Sai mật khẩu 5 lần sẽ khóa đăng nhập 15 phút.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng nhập thành công", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_FAILED hoặc INVALID_REQUEST", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS: email hoặc mật khẩu không đúng", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "ACCOUNT_LOCKED: tài khoản đang bị khóa đăng nhập", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Lỗi hệ thống", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = loginUseCase.execute(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
