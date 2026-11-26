package com.lumintra.backend.iam.api.request;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.hibernate.validator.constraints.URL;

import java.util.Locale;

@Schema(
        description =
                "Provide at least one field. Omitted and null values preserve existing fields.")
public record UpdateManagedUserRequest(
        @Schema(example = "Nguyen Van An") @Size(min = 1, max = 255) String name,
        @Schema(example = "+84901234567") @Pattern(regexp = "^\\+?[0-9]{8,15}$") String phone,
        @Schema(example = "https://example.com/avatar.png")
                @Size(max = 255)
                @Pattern(regexp = "^https?://\\S+$")
                @URL
                String avatar,
        @Schema(example = "MANAGER") @Size(min = 1, max = 100) String role,
        @Schema(description = "Set or clear a manual suspension", example = "true")
                Boolean locked) {
    public UpdateManagedUserRequest {
        name = trim(name);
        phone = trim(phone);
        avatar = trim(avatar);
        role = role == null ? null : role.strip().toUpperCase(Locale.ROOT);
    }

    @JsonIgnore
    @AssertTrue(message = "Provide at least one editable field")
    public boolean isUpdateSpecified() {
        return name != null || phone != null || avatar != null || role != null || locked != null;
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
