package com.lumintra.backend.iam.api.response;

import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RegisterResponse {
    private Long id;
    private String email;
    private String accountType;
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