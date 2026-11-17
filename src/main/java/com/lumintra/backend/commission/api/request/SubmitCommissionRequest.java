package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

        @Schema(description = "Pricing tier code (e.g. BASIC, ADVANCED, CUSTOM)", example = "ADVANCED", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Tier code is required")
        @Size(max = 50, message = "Tier code must not exceed 50 characters")
        String tierCode,

        @Schema(description = "Foundational Step-1 configuration selections (FORMAT_TYPE, MATRIX_SIZE, FRAME_RANGE)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Step-1 foundational selections are required")
        List<@Valid Step1SelectionItem> step1Selections,

        @Schema(description = "Step-2 advanced configurator options or add-ons")
        List<@Valid SelectedOptionItem> selectedOptions,

        @Schema(description = "Optional target matrix dimensions (e.g. 16x16, 32x32, 64x64). If null, derived from Step-1 MATRIX_SIZE selection", example = "32x32")
        @Size(max = 50, message = "Target matrix dimensions must not exceed 50 characters")
        String targetMatrixDimensions,

        @Schema(description = "Optional reference image attachments uploaded beforehand via media service (max 5)")
        @Size(max = 5, message = "A maximum of 5 reference attachments is allowed")
        List<@Valid CommissionAttachmentItemRequest> attachments
) {
    public SubmitCommissionRequest {
        if (title != null) {
            title = title.trim();
        }
        if (description != null) {
            description = description.trim();
        }
        if (tierCode != null) {
            tierCode = tierCode.trim();
        }
        if (targetMatrixDimensions != null && !targetMatrixDimensions.isBlank()) {
            targetMatrixDimensions = targetMatrixDimensions.trim();
        } else {
            targetMatrixDimensions = null;
        }
        if (step1Selections == null) {
            step1Selections = List.of();
        }
        if (selectedOptions == null) {
            selectedOptions = List.of();
        }
        if (attachments == null) {
            attachments = List.of();
        }
    }

    public record Step1SelectionItem(
            @Schema(description = "Pricing config rule identifier", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "configId is required")
            Long configId,

            @Schema(description = "Selected option identifier", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "optionId is required")
            Long optionId
    ) {}

    public record SelectedOptionItem(
            @Schema(description = "Pricing config rule identifier", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "configId is required")
            Long configId,

            @Schema(description = "Selected option identifiers for single-select or multi-select rules", example = "[10]")
            List<Long> optionIds,

            @Schema(description = "Custom textual value for text input rules", example = "Glowing neon background")
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

    public record CommissionAttachmentItemRequest(
            @Schema(description = "URL of the uploaded reference image", example = "https://cdn.lumintra.com/commissions/references/42/ref-01.png", requiredMode = Schema.RequiredMode.REQUIRED)
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
