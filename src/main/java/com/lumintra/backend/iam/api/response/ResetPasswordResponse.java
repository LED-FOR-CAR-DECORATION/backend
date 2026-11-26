package com.lumintra.backend.iam.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Acknowledgement of successful password reset")
public record ResetPasswordResponse(
        @Schema(
                description = "Success message",
                example = "Password has been reset successfully. Please log in with your new password.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String message
) {}
