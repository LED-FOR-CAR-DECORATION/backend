package com.lumintra.backend.iam.infrastructure.persistence.mapper;

import com.lumintra.backend.iam.api.response.ManagedUserDetailResponse;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

@Component
public class ManagedUserDetailMapper {
    public ManagedUserDetailResponse toResponse(EmployeeEntity employee, Instant snapshotTime) {
        var account = employee.getAccount();
        String avatar = employee.getAvatarUrl();
        if (avatar == null || avatar.isBlank()) {
            avatar = account.getAvatarUrl();
        }
        Instant until = account.getLockoutUntil();
        return ManagedUserDetailResponse.builder()
                .accountId(account.getAccountId())
                .name(employee.getFullName())
                .email(account.getEmail())
                .phone(employee.getPhone())
                .avatar(avatar)
                .role(employee.getRole().getRoleName().toUpperCase(Locale.ROOT))
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .lockout(
                        new ManagedUserDetailResponse.LockoutState(
                                until != null && until.isAfter(snapshotTime),
                                until,
                                account.getFailedAttempts()))
                .build();
    }
}
