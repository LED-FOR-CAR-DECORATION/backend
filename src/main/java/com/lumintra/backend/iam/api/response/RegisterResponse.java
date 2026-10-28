package com.lumintra.backend.iam.api.response;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Registration account state. Verify the email before signing in.")
public record RegisterResponse(@Schema(example="123") Long userId,
                               @Schema(example="customer@example.com") String email,
                               @Schema(example="PENDING_VERIFICATION") String status) {}
