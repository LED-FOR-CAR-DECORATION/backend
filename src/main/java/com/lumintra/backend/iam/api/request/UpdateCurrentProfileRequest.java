package com.lumintra.backend.iam.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

@Schema(description = "Replace editable profile fields. Null or blank phone/avatar clears the profile value; name is required.")
public record UpdateCurrentProfileRequest(
        @Schema(description = "Full name, trimmed, 1 to 255 characters", example = "Nguyen Van An", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @Schema(description = "8 to 15 digits with an optional leading plus. Null or blank clears the phone.", example = "+84901234567", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Phone must contain 8 to 15 digits with an optional leading plus")
        String phone,

        @Schema(description = "Absolute HTTP(S) avatar URL, maximum 255 characters. Null or blank clears the profile override, falling back to the account avatar.", example = "https://example.com/avatar.png", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 255, message = "Avatar URL must not exceed 255 characters")
        @Pattern(regexp = "^https?://\\S+$", message = "Avatar must be an HTTP or HTTPS URL")
        @URL(message = "Avatar must be a valid URL")
        String avatar
) {
    public UpdateCurrentProfileRequest {
        name = name == null ? null : name.strip();
        phone = optional(phone);
        avatar = optional(avatar);
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
