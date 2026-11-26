package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request payload for submitting a custom artwork commission request")
public record SubmitCommissionRequest(
        @Schema(description = "Title of the custom commission artwork", example = "Cyberpunk Shiba Inu Pixel Art", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Schema(description = "Detailed description of the artwork requirements", example = "Pixel art shiba with blinking neon effects...")
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @Schema(description = "Pricing tier code (defaults to CUSTOM when omitted; supports active presets if configured)", example = "CUSTOM", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 50, message = "Tier code must not exceed 50 characters")
        String tierCode,

        @Schema(description = "Unified rule selections across active SINGLE_SELECT, MULTI_SELECT, QUANTITY, and TEXT_INPUT pricing rules")
        List<@Valid SelectedOptionItem> selectedOptions,

        @Schema(description = "Optional reference image attachments uploaded beforehand via media service (max 5)")
        @Size(max = 5, message = "A maximum of 5 reference attachments is allowed")
        List<@Valid CommissionAttachmentItemRequest> attachments,

        @Schema(description = "Open initial checkout immediately for the server-calculated price")
        Boolean openPaymentImmediately
) {
    public SubmitCommissionRequest(String title, String description, String tierCode,
                                   List<SelectedOptionItem> selectedOptions,
                                   List<CommissionAttachmentItemRequest> attachments) {
        this(title, description, tierCode, selectedOptions, attachments, false);
    }

    public SubmitCommissionRequest {
        if (title != null) {
            title = title.trim();
        }
        if (description != null) {
            description = description.trim();
        }
        if (tierCode != null && !tierCode.isBlank()) {
            tierCode = tierCode.trim();
        } else {
            tierCode = "CUSTOM";
        }
        if (selectedOptions == null) {
            selectedOptions = List.of();
        }
        if (attachments == null) {
            attachments = List.of();
        }
    }

    @Schema(description = "Customer selection for a single active commission pricing rule")
    public record SelectedOptionItem(
            @Schema(description = "Pricing config rule identifier", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "configId is required")
            Long configId,

            @Schema(description = "Selected option identifiers for SINGLE_SELECT or MULTI_SELECT rules; optional for QUANTITY/TEXT_INPUT", example = "[2]")
            List<Long> optionIds,

            @Schema(description = "Numeric quantity value for QUANTITY rules within [minQuantity, maxQuantity]", example = "2", nullable = true)
            @Min(value = 0, message = "quantity must be greater than or equal to 0")
            Integer quantity,

            @Schema(description = "Custom textual value for TEXT_INPUT rules", example = "Glowing neon cyan outline", nullable = true)
            @Size(max = 500, message = "textValue must not exceed 500 characters")
            String textValue
    ) {
        public SelectedOptionItem {
            if (optionIds == null) {
                optionIds = List.of();
            }
            if (textValue != null && !textValue.isBlank()) {
                textValue = textValue.trim();
            } else {
                textValue = null;
            }
        }
    }

    @Schema(description = "Reference image attachment item")
    public record CommissionAttachmentItemRequest(
            @Schema(description = "objectKey returned by POST /api/media/upload-url with purpose COMMISSION_REFERENCE by this customer", example = "commissions/references/42/123e4567-e89b-12d3-a456-426614174000-ref-01.png", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "imageUrl is required")
            @Size(max = 1024, message = "imageUrl must not exceed 1024 characters")
            String imageUrl,

            @Schema(description = "Optional caption for the reference image", example = "Neon color palette reference")
            @Size(max = 255, message = "caption must not exceed 255 characters")
            String caption
    ) {
        public CommissionAttachmentItemRequest {
            if (imageUrl != null) {
                imageUrl = imageUrl.trim();
            }
            if (caption != null && !caption.isBlank()) {
                caption = caption.trim();
            } else {
                caption = null;
            }
        }
    }
}
