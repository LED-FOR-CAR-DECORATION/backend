package com.lumintra.backend.iam.api.response;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Generic acknowledgement that does not reveal whether an account exists")
public record ForgotPasswordResponse(
    @Schema(description = "Request acknowledgement", example = "If the email is registered, a password reset code has been sent.", requiredMode = Schema.RequiredMode.REQUIRED) String message
) {}
