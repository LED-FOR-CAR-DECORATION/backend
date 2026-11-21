package com.lumintra.backend.device.api.request;

import com.lumintra.backend.device.domain.model.FirmwareVersionNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "multipart/form-data payload for uploading a firmware release (DEV-12)")
public record UploadFirmwareRequest(
        @Schema(description = "ESP32 firmware image (.bin); must start with the 0xE9 magic byte",
                type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "file is required")
        MultipartFile file,

        @Schema(description = "Target LED model id (DEPRECATED models are allowed)", example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "modelId is required")
        @Positive(message = "modelId must be a positive integer")
        Long modelId,

        @Schema(description = "Semantic version MAJOR.MINOR.PATCH, higher than every existing version of the model",
                example = "1.2.0", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "version is required")
        @Size(max = 32, message = "version cannot exceed 32 characters")
        @Pattern(regexp = FirmwareVersionNumber.PATTERN, message = "version must follow MAJOR.MINOR.PATCH, e.g. 1.2.0")
        String version,

        @Schema(description = "Release notes shown to admins and customers", example = "Fix BLE reconnect after sleep",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "changelog is required")
        @Size(max = 5000, message = "changelog cannot exceed 5000 characters")
        String changelog
) {
}
