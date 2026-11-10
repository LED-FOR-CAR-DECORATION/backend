package com.lumintra.backend.iam.api.response;

import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.shared.response.PaginationMeta;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated internal users, ordered by accountId descending")
public record ManagedUserListResponse(
        @Schema(
                        description = "Safe internal-user summaries",
                        example = "[]",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<UserItem> items,
        @Schema(
                        description = "Standard pagination metadata",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                PaginationMeta meta) {
    public ManagedUserListResponse {
        items = List.copyOf(items);
    }

    @Schema(description = "Internal account profile; excludes credentials and security counters")
    public record UserItem(
            @Schema(
                            description = "Account ID",
                            example = "42",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    Long accountId,
            @Schema(
                            description = "Employee full name",
                            example = "Nguyen Van An",
                            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    String name,
            @Schema(
                            description = "Account email",
                            example = "staff@example.com",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String email,
            @Schema(
                            description = "Employee phone",
                            example = "0901234567",
                            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    String phone,
            @Schema(
                            description = "Employee avatar, falling back to account avatar",
                            example = "https://example.com/avatar.png",
                            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    String avatar,
            @Schema(
                            description = "Current database role names without ROLE_ prefix",
                            example = "[\"MANAGER\"]",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    List<String> roles,
            @Schema(
                            description = "Account lifecycle status",
                            example = "ACTIVE",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    AccountStatus status,
            @Schema(
                            description = "Whether account status is ACTIVE",
                            example = "true",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    boolean active) {
        public UserItem {
            roles = List.copyOf(roles);
        }
    }
}
