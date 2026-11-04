package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.request.GoogleLoginRequest;
import com.lumintra.backend.iam.api.request.LogoutRequest;
import com.lumintra.backend.iam.api.request.RefreshTokenRequest;
import jakarta.servlet.http.HttpServletRequest;
import com.lumintra.backend.iam.api.request.RegisterRequest;
import com.lumintra.backend.iam.api.request.ResendVerificationRequest;
import com.lumintra.backend.iam.api.request.VerifyEmailRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import com.lumintra.backend.shared.config.OpenApiConfig;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerSwaggerTest {

    @Test
    @DisplayName("AuthController should have Swagger @Tag annotation")
    void authControllerShouldHaveTagAnnotation() {
        Tag tag = AuthController.class.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("Authentication");
        assertThat(tag.description()).isNotEmpty();
    }

    @Test
    @DisplayName("register endpoint should have Swagger @Operation and documentation details")
    void registerEndpointShouldHaveOperationAnnotation() throws NoSuchMethodException {
        Method registerMethod = AuthController.class.getMethod("register", RegisterRequest.class);
        Operation operation = registerMethod.getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("Register a new customer account");
        assertThat(operation.description()).contains("Registers a new customer account");
    }

    @Test
    @DisplayName("register endpoint should have documented @ApiResponses for 201, 400, 409, and 500")
    void registerEndpointShouldHaveDocumentedApiResponses() throws NoSuchMethodException {
        Method registerMethod = AuthController.class.getMethod("register", RegisterRequest.class);
        ApiResponses apiResponses = registerMethod.getAnnotation(ApiResponses.class);

        assertThat(apiResponses).isNotNull();
        Map<String, io.swagger.v3.oas.annotations.responses.ApiResponse> responseMap =
                Arrays.stream(apiResponses.value())
                        .collect(Collectors.toMap(
                                io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode,
                                r -> r
                        ));

        assertThat(responseMap).containsKeys("201", "400", "409", "500");
        assertThat(responseMap.get("201").description()).contains("successfully");
        assertThat(responseMap.get("400").description()).contains("Validation failed");
        assertThat(responseMap.get("409").description()).contains("already registered");
        assertThat(responseMap.get("500").description()).contains("Internal server error");
    }

    @Test
    @DisplayName("RegisterRequest record should document fields with @Schema annotations")
    void registerRequestShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = RegisterRequest.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        String[] fields = {"email", "password", "confirmPassword"};
        for (String fieldName : fields) {
            Schema schema = RegisterRequest.class.getDeclaredField(fieldName).getAnnotation(Schema.class);
            assertThat(schema)
                    .withFailMessage("Missing @Schema on record field %s", fieldName)
                    .isNotNull();
            assertThat(schema.description()).isNotEmpty();
            assertThat(schema.example()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("RegisterResponse class should document fields with @Schema annotations")
    void registerResponseShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = RegisterResponse.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        String[] fields = {"id", "email", "accountType", "status"};
        for (String fieldName : fields) {
            Schema fieldSchema = RegisterResponse.class.getDeclaredField(fieldName).getAnnotation(Schema.class);
            assertThat(fieldSchema)
                    .withFailMessage("Missing @Schema on field %s", fieldName)
                    .isNotNull();
            assertThat(fieldSchema.description()).isNotEmpty();
            assertThat(fieldSchema.example()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("ApiResponse envelope should have @Schema annotations")
    void apiResponseShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = ApiResponse.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        assertThat(ApiResponse.class.getDeclaredField("data").getAnnotation(Schema.class)).isNotNull();
        assertThat(ApiResponse.class.getDeclaredField("meta").getAnnotation(Schema.class)).isNotNull();
    }

    @Test
    @DisplayName("ErrorResponse envelope should have @Schema annotations")
    void errorResponseShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = ErrorResponse.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        String[] fields = {"code", "message", "status", "timestamp", "traceId", "details"};
        for (String fieldName : fields) {
            assertThat(ErrorResponse.class.getDeclaredField(fieldName).getAnnotation(Schema.class)).isNotNull();
        }
    }

    @Test
    @DisplayName("login endpoint should have Swagger @Operation and documentation details")
    void loginEndpointShouldHaveOperationAnnotation() throws NoSuchMethodException {
        Method loginMethod = AuthController.class.getMethod("login", LoginRequest.class);
        Operation operation = loginMethod.getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("Authenticate user credentials");
        assertThat(operation.description()).contains("Authenticates user credentials");
    }

    @Test
    @DisplayName("login endpoint should have documented @ApiResponses for 200, 400, 401, 423, and 500")
    void loginEndpointShouldHaveDocumentedApiResponses() throws NoSuchMethodException {
        Method loginMethod = AuthController.class.getMethod("login", LoginRequest.class);
        ApiResponses apiResponses = loginMethod.getAnnotation(ApiResponses.class);

        assertThat(apiResponses).isNotNull();
        Map<String, io.swagger.v3.oas.annotations.responses.ApiResponse> responseMap =
                Arrays.stream(apiResponses.value())
                        .collect(Collectors.toMap(
                                io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode,
                                r -> r
                        ));

        assertThat(responseMap).containsKeys("200", "400", "401", "423", "500");
        assertThat(responseMap.get("200").description()).contains("successfully");
        assertThat(responseMap.get("400").description()).contains("Validation failed");
        assertThat(responseMap.get("401").description()).contains("Unauthorized");
        assertThat(responseMap.get("423").description()).contains("Locked");
        assertThat(responseMap.get("500").description()).contains("Internal server error");
    }

    @Test
    @DisplayName("LoginRequest record should document fields with @Schema annotations")
    void loginRequestShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = LoginRequest.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        String[] fields = {"email", "password"};
        for (String fieldName : fields) {
            Schema schema = LoginRequest.class.getDeclaredField(fieldName).getAnnotation(Schema.class);
            assertThat(schema)
                    .withFailMessage("Missing @Schema on record field %s", fieldName)
                    .isNotNull();
            assertThat(schema.description()).isNotEmpty();
            assertThat(schema.example()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("LoginResponse class should document fields with @Schema annotations")
    void loginResponseShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = LoginResponse.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        String[] fields = {"accessToken", "refreshToken", "tokenType", "expiresIn"};
        for (String fieldName : fields) {
            Schema fieldSchema = LoginResponse.class.getDeclaredField(fieldName).getAnnotation(Schema.class);
            assertThat(fieldSchema)
                    .withFailMessage("Missing @Schema on field %s", fieldName)
                    .isNotNull();
            assertThat(fieldSchema.description()).isNotEmpty();
            assertThat(fieldSchema.example()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("verifyEmail endpoint should have Swagger @Operation and documentation details")
    void verifyEmailEndpointShouldHaveOperationAnnotation() throws NoSuchMethodException {
        Method verifyMethod = AuthController.class.getMethod("verifyEmail", VerifyEmailRequest.class);
        Operation operation = verifyMethod.getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("Verify customer email with OTP");
        assertThat(operation.description()).contains("Validates the 6-digit OTP code");
    }

    @Test
    @DisplayName("verifyEmail endpoint should have documented @ApiResponses for 200, 400, 404, 429, and 500")
    void verifyEmailEndpointShouldHaveDocumentedApiResponses() throws NoSuchMethodException {
        Method verifyMethod = AuthController.class.getMethod("verifyEmail", VerifyEmailRequest.class);
        ApiResponses apiResponses = verifyMethod.getAnnotation(ApiResponses.class);

        assertThat(apiResponses).isNotNull();
        Map<String, io.swagger.v3.oas.annotations.responses.ApiResponse> responseMap =
                Arrays.stream(apiResponses.value())
                        .collect(Collectors.toMap(
                                io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode,
                                r -> r
                        ));

        assertThat(responseMap).containsKeys("200", "400", "404", "429", "500");
        assertThat(responseMap.get("200").description()).contains("successfully");
        assertThat(responseMap.get("400").description()).contains("Validation failed");
        assertThat(responseMap.get("404").description()).contains("not found");
        assertThat(responseMap.get("429").description()).contains("Too many failed OTP attempts");
        assertThat(responseMap.get("500").description()).contains("Internal server error");
    }

    @Test
    @DisplayName("VerifyEmailRequest record should document fields with @Schema annotations")
    void verifyEmailRequestShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = VerifyEmailRequest.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        String[] fields = {"email", "otp"};
        for (String fieldName : fields) {
            Schema schema = VerifyEmailRequest.class.getDeclaredField(fieldName).getAnnotation(Schema.class);
            assertThat(schema)
                    .withFailMessage("Missing @Schema on record field %s", fieldName)
                    .isNotNull();
            assertThat(schema.description()).isNotEmpty();
            assertThat(schema.example()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("resendVerification endpoint should have Swagger @Operation and documentation details")
    void resendVerificationEndpointShouldHaveOperationAnnotation() throws NoSuchMethodException {
        Method resendMethod = AuthController.class.getMethod("resendVerification", ResendVerificationRequest.class);
        Operation operation = resendMethod.getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("Resend verification email");
        assertThat(operation.description()).contains("Re-generates and emails a new 6-digit verification OTP");
    }

    @Test
    @DisplayName("resendVerification endpoint should have documented @ApiResponses for 202, 400, 404, 429, and 500")
    void resendVerificationEndpointShouldHaveDocumentedApiResponses() throws NoSuchMethodException {
        Method resendMethod = AuthController.class.getMethod("resendVerification", ResendVerificationRequest.class);
        ApiResponses apiResponses = resendMethod.getAnnotation(ApiResponses.class);

        assertThat(apiResponses).isNotNull();
        Map<String, io.swagger.v3.oas.annotations.responses.ApiResponse> responseMap =
                Arrays.stream(apiResponses.value())
                        .collect(Collectors.toMap(
                                io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode,
                                r -> r
                        ));

        assertThat(responseMap).containsKeys("202", "400", "404", "429", "500");
        assertThat(responseMap.get("202").description()).contains("accepted");
        assertThat(responseMap.get("400").description()).contains("Validation failed");
        assertThat(responseMap.get("404").description()).contains("not found");
        assertThat(responseMap.get("429").description()).contains("Rate limit exceeded");
        assertThat(responseMap.get("500").description()).contains("Internal server error");
    }

    @Test
    @DisplayName("ResendVerificationRequest record should document fields with @Schema annotations")
    void resendVerificationRequestShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = ResendVerificationRequest.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        Schema schema = ResendVerificationRequest.class.getDeclaredField("email").getAnnotation(Schema.class);
        assertThat(schema).isNotNull();
        assertThat(schema.description()).isNotEmpty();
        assertThat(schema.example()).isNotEmpty();
    }

    @Test
    @DisplayName("OpenApiConfig should produce OpenAPI bean with title and version")
    void openApiConfigShouldProduceValidMetadata() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.customOpenAPI();

        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Lumintra Backend API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("1.0.0");
    }
    @Test
    @DisplayName("googleLogin endpoint should have Swagger @Operation and documentation details")
    void googleLoginEndpointShouldHaveOperationAnnotation() throws NoSuchMethodException {
        Method googleMethod = AuthController.class.getMethod("googleLogin", GoogleLoginRequest.class);
        Operation operation = googleMethod.getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("Authenticate or register via Google OAuth2");
        assertThat(operation.description()).contains("Authenticates a customer using a Google ID token");
    }

    @Test
    @DisplayName("googleLogin endpoint should have documented @ApiResponses for 200, 400, 401, 423, and 500")
    void googleLoginEndpointShouldHaveDocumentedApiResponses() throws NoSuchMethodException {
        Method googleMethod = AuthController.class.getMethod("googleLogin", GoogleLoginRequest.class);
        ApiResponses apiResponses = googleMethod.getAnnotation(ApiResponses.class);

        assertThat(apiResponses).isNotNull();
        Map<String, io.swagger.v3.oas.annotations.responses.ApiResponse> responseMap =
                Arrays.stream(apiResponses.value())
                        .collect(Collectors.toMap(
                                io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode,
                                r -> r
                        ));

        assertThat(responseMap).containsKeys("200", "400", "401", "423", "500");
        assertThat(responseMap.get("200").description()).contains("successfully via Google OAuth2");
        assertThat(responseMap.get("400").description()).contains("Validation failed");
        assertThat(responseMap.get("401").description()).contains("Unauthorized");
        assertThat(responseMap.get("423").description()).contains("Locked");
        assertThat(responseMap.get("500").description()).contains("Internal server error");
    }

    @Test
    @DisplayName("GoogleLoginRequest record should document fields with @Schema annotations")
    void googleLoginRequestShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = GoogleLoginRequest.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        Schema schema = GoogleLoginRequest.class.getDeclaredField("idToken").getAnnotation(Schema.class);
        assertThat(schema).isNotNull();
        assertThat(schema.description()).isNotEmpty();
        assertThat(schema.example()).isNotEmpty();
    }
    @Test
    @DisplayName("refreshToken endpoint should have Swagger @Operation and documentation details")
    void refreshTokenEndpointShouldHaveOperationAnnotation() throws NoSuchMethodException {
        Method refreshMethod = AuthController.class.getMethod("refreshToken", RefreshTokenRequest.class);
        Operation operation = refreshMethod.getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("Refresh access and refresh JWT tokens");
        assertThat(operation.description()).contains("Validates the provided refresh token");
    }

    @Test
    @DisplayName("refreshToken endpoint should have documented @ApiResponses for 200, 400, 401, and 500")
    void refreshTokenEndpointShouldHaveDocumentedApiResponses() throws NoSuchMethodException {
        Method refreshMethod = AuthController.class.getMethod("refreshToken", RefreshTokenRequest.class);
        ApiResponses apiResponses = refreshMethod.getAnnotation(ApiResponses.class);

        assertThat(apiResponses).isNotNull();
        Map<String, io.swagger.v3.oas.annotations.responses.ApiResponse> responseMap =
                Arrays.stream(apiResponses.value())
                        .collect(Collectors.toMap(
                                io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode,
                                r -> r
                        ));

        assertThat(responseMap).containsKeys("200", "400", "401", "500");
        assertThat(responseMap.get("200").description()).contains("Tokens renewed successfully");
        assertThat(responseMap.get("400").description()).contains("Validation failed");
        assertThat(responseMap.get("401").description()).contains("Unauthorized");
        assertThat(responseMap.get("500").description()).contains("Internal server error");
    }

    @Test
    @DisplayName("RefreshTokenRequest record should document fields with @Schema annotations")
    void refreshTokenRequestShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = RefreshTokenRequest.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        Schema schema = RefreshTokenRequest.class.getDeclaredField("refreshToken").getAnnotation(Schema.class);
        assertThat(schema).isNotNull();
        assertThat(schema.description()).isNotEmpty();
        assertThat(schema.example()).isNotEmpty();
    }
    @Test
    @DisplayName("logout endpoint should have Swagger @Operation and documentation details")
    void logoutEndpointShouldHaveOperationAnnotation() throws NoSuchMethodException {
        Method logoutMethod = AuthController.class.getMethod("logout", LogoutRequest.class, HttpServletRequest.class);
        Operation operation = logoutMethod.getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("Log out user session and invalidate tokens");
        assertThat(operation.description()).contains("Logs out the authenticated user");
    }

    @Test
    @DisplayName("logout endpoint should have documented @ApiResponses for 204, 400, and 500")
    void logoutEndpointShouldHaveDocumentedApiResponses() throws NoSuchMethodException {
        Method logoutMethod = AuthController.class.getMethod("logout", LogoutRequest.class, HttpServletRequest.class);
        ApiResponses apiResponses = logoutMethod.getAnnotation(ApiResponses.class);

        assertThat(apiResponses).isNotNull();
        Map<String, io.swagger.v3.oas.annotations.responses.ApiResponse> responseMap =
                Arrays.stream(apiResponses.value())
                        .collect(Collectors.toMap(
                                io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode,
                                r -> r
                        ));

        assertThat(responseMap).containsKeys("204", "400", "500");
        assertThat(responseMap.get("204").description()).contains("Session successfully terminated");
        assertThat(responseMap.get("400").description()).contains("Validation failed");
        assertThat(responseMap.get("500").description()).contains("Internal server error");
    }

    @Test
    @DisplayName("LogoutRequest record should document fields with @Schema annotations")
    void logoutRequestShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = LogoutRequest.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        Schema schema = LogoutRequest.class.getDeclaredField("refreshToken").getAnnotation(Schema.class);
        assertThat(schema).isNotNull();
        assertThat(schema.description()).isNotEmpty();
        assertThat(schema.example()).isNotEmpty();
    }
}
