package com.lumintra.backend.iam.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Current account profile; only public profile fields are serialized")
public record CurrentProfileResponse(
        @Schema(
                        description = "Account identifier",
                        example = "42",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long accountId,
        @Schema(
                        description = "Full name; null when not completed",
                        example = "Nguyen Van An",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String name,
        @Schema(
                        description = "Account email",
                        example = "user@example.com",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String email,
        @Schema(
                        description = "Phone; null when not completed",
                        example = "0901234567",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String phone,
        @Schema(
                        description = "Profile avatar URL, falling back to the account avatar",
                        example = "https://example.com/avatar.png",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String avatar,
        @Schema(
                        description = "Current database role names without ROLE_ prefix",
                        example = "[\"CUSTOMER\"]",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<String> roles) {
    public CurrentProfileResponse {
        roles = List.copyOf(roles);
    }
}
