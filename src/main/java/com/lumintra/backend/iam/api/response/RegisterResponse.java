package com.lumintra.backend.iam.api.response;

import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "Customer registration response data")
@Getter
@Builder
public class RegisterResponse {

    @Schema(description = "Unique ID of the created user account", example = "101")
    private Long id;

    @Schema(description = "Registered email address", example = "customer@example.com")
    private String email;

    @Schema(description = "Account type assigned to the user", example = "CUSTOMER")
    private String accountType;

    @Schema(description = "Initial account verification status", example = "PENDING_VERIFICATION")
    private String status;

    public static RegisterResponse from(UserAccountEntity account) {
        return RegisterResponse.builder()
                .id(account.getAccountId())
                .email(account.getEmail())
                .accountType(account.getAccountType().name())
                .status(account.getStatus().name())
                .build();
    }
}