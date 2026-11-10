package com.lumintra.backend.iam.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Locale;

@Schema(description = "Internal user list filters, combined with AND. CUSTOMER accounts/roles are always excluded.")
public record ManagedUserFilterRequest(
        @Schema(description = "Zero-based page index, 0 to 1000000", example = "0", defaultValue = "0", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Min(0) @Max(1000000) Integer page,
        @Schema(description = "Page size, 1 to 50", example = "10", defaultValue = "10", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Min(1) @Max(50) Integer size,
        @Schema(description = "Exact role name, case-insensitive, without ROLE_ prefix", example = "MANAGER", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 100) String role,
        @Schema(description = "True selects ACTIVE; false selects all non-ACTIVE statuses; omitted selects both", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Boolean active,
        @Schema(description = "Literal case-insensitive substring of account email or employee name. Blank means no search.", example = "nguyen", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 255) String search
) {
    public ManagedUserFilterRequest {
        page = page == null ? 0 : page;
        size = size == null ? 10 : size;
        role = blankToNull(role);
        role = role == null ? null : role.toUpperCase(Locale.ROOT);
        search = blankToNull(search);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
