package com.lumintra.backend.character.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

@Schema(description = "Request payload for uploading and creating a new character animation version")
public record CreateCharacterVersionRequest(
        @Schema(description = "Public CDN URL of the animated preview GIF", example = "https://cdn.lumintra.com/characters/samurai-v2.gif")
        @NotBlank(message = "Preview GIF URL must not be blank.")
        @Size(max = 500, message = "Preview GIF URL must not exceed 500 characters.")
        @Pattern(regexp = "https?://\\S+", message = "Preview GIF URL must use http or https scheme.")
        @URL(message = "Preview GIF URL must be a valid URL.")
        String previewGifUrl,

        @Schema(description = "Total number of animation frames in the sequence", example = "16")
        @NotNull(message = "Frame count is required.")
        @Positive(message = "Frame count must be greater than 0.")
        Integer frameCount,

        @Schema(description = "Duration to display each frame in milliseconds", example = "100")
        @NotNull(message = "Frame delay is required.")
        @Positive(message = "Frame delay must be greater than 0.")
        Integer frameDelayMs,

        @Schema(description = "Byte size of the animation payload", example = "65536")
        @NotNull(message = "Byte size is required.")
        @Positive(message = "Byte size must be greater than 0.")
        Integer byteSize,

        @Schema(description = "Whether to set this newly uploaded version as active current revision (defaults to true)", example = "true")
        Boolean isCurrent
) {
    public CreateCharacterVersionRequest {
        if (isCurrent == null) {
            isCurrent = true;
        }
    }
}
