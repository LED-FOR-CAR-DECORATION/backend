package com.lumintra.backend.device.api.request;

import com.lumintra.backend.device.api.request.validation.ValidWarrantyResolution;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for adjudicating and transitioning a warranty claim status")
@ValidWarrantyResolution
public record ProcessWarrantyClaimRequest(
        @Schema(
                description = "New lifecycle status of the claim (APPROVED, REJECTED, or COMPLETED)",
                example = "APPROVED",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "status must not be null")
        WarrantyTicketStatus status,

        @Schema(
                description = "Resolution or rejection notes explaining the decision or service provided (required for REJECTED and COMPLETED)",
                example = "Yêu cầu bảo hành hợp lệ. Mời khách hàng mang thiết bị đến cửa hàng Lumintra số 123 Cầu Giấy trong giờ hành chính để kỹ thuật viên kiểm tra và thay thế mạch LED mới.",
                nullable = true
        )
        @Size(max = 1000, message = "resolutionNotes must not exceed 1000 characters")
        String resolutionNotes
) {
    public ProcessWarrantyClaimRequest {
        resolutionNotes = (resolutionNotes != null && !resolutionNotes.isBlank()) ? resolutionNotes.strip() : null;
    }
}
