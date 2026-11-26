package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Revision brief card rejected by the customer")
public record RejectRevisionCardRequest(
        @NotNull
                @Positive
                @Schema(
                        description = "Message ID of the latest pending revision brief card",
                        example = "402",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long messageId,
        @Size(max = 500, message = "reason must not exceed 500 characters")
                @Schema(
                        description =
                                "Optional customer reason explaining why the brief card does not"
                                        + " match their request",
                        example = "Sai màu ngôi sao, mình muốn màu cyan #00E5FF.",
                        nullable = true)
                String reason) {}
