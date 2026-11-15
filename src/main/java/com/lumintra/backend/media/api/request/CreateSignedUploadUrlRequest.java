package com.lumintra.backend.media.api.request;

import com.lumintra.backend.media.domain.model.MediaPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for generating a short-lived V4 signed upload URL")
public record CreateSignedUploadUrlRequest(

        @Schema(description = "Original filename of the file to be uploaded", example = "samurai-anim.gif")
        @NotBlank(message = "fileName must not be blank")
        @Size(max = 255, message = "fileName must not exceed 255 characters")
        String fileName,

        @Schema(description = "MIME content type of the image", example = "image/gif")
        @NotBlank(message = "contentType must not be blank")
        @Pattern(
                regexp = "^(image/jpeg|image/png|image/webp|image/gif|image/svg\\+xml)$",
                message = "contentType must be one of: image/jpeg, image/png, image/webp, image/gif, image/svg+xml"
        )
        String contentType,

        @Schema(description = "Exact size of the file in bytes (max 10MB)", example = "65536")
        @NotNull(message = "fileSize must not be null")
        @Min(value = 1, message = "fileSize must be at least 1 byte")
        @Max(value = 10485760, message = "fileSize must not exceed 10MB (10485760 bytes)")
        Long fileSize,

        @Schema(description = "Strict business purpose categorization for folder scoping and access control", example = "CHARACTER_ASSET")
        @NotNull(message = "purpose must not be null")
        MediaPurpose purpose,

        @Schema(description = "Optional context identifier (commissionId or deviceId) required for specific purposes", example = "101")
        Long targetId
) {
}
