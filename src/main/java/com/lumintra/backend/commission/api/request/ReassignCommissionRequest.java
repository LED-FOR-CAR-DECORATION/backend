package com.lumintra.backend.commission.api.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Schema(description = "CMS-25: manager/admin override of a commission's assigned staff member and/or designer")
@Builder
public record ReassignCommissionRequest(
        @Schema(description = "New assigned STAFF employee ID; omit to keep the current staff member", example = "4", nullable = true)
        @Positive(message = "staffId must be greater than 0")
        Long staffId,

        @Schema(description = "New assigned DESIGNER employee ID; omit to keep the current designer", example = "6", nullable = true)
        @Positive(message = "designerId must be greater than 0")
        Long designerId,

        @Schema(description = "Why the ticket is being reassigned (internal, never shown to the customer)",
                example = "Designer on leave", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason cannot exceed 500 characters")
        String reason
) {
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "At least one of staffId or designerId is required")
    public boolean isTargetProvided() {
        return staffId != null || designerId != null;
    }
}
