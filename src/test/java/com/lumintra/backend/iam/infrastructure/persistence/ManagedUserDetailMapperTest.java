package com.lumintra.backend.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.mapper.ManagedUserDetailMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

class ManagedUserDetailMapperTest {
    @Test
    @DisplayName(
            "B: Map full safe profile and distinguish null, expired, exact-deadline and future"
                    + " lockouts")
    void profileAndLockoutBoundaries() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        var account =
                UserAccountEntity.createCustomerAccount(
                        "staff@example.com", "private-password-hash");
        account.setAccountId(42L);
        account.setAccountType(AccountType.EMPLOYEE);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAvatarUrl("https://example.com/account.png");
        account.setFailedAttempts(5);
        account.setCreatedAt(now.minusSeconds(100));
        var employee = new EmployeeEntity();
        employee.setAccount(account);
        employee.setFullName("Nguyen Van An");
        employee.setPhone("0901234567");
        employee.setUpdatedAt(now.minusSeconds(10));
        var role = new RoleEntity();
        role.setRoleName("staff");
        employee.setRole(role);
        var mapper = new ManagedUserDetailMapper();
        for (Instant until : new Instant[] {null, now.minusSeconds(1), now, now.plusSeconds(1)}) {
            account.setLockoutUntil(until);
            var result = mapper.toResponse(employee, now);
            assertThat(result.lockout().locked()).isEqualTo(until != null && until.isAfter(now));
            assertThat(result.lockout().lockoutUntil()).isEqualTo(until);
            assertThat(result.lockout().failedAttempts()).isEqualTo(5);
            assertThat(result.name()).isEqualTo("Nguyen Van An");
            assertThat(result.email()).isEqualTo("staff@example.com");
            assertThat(result.phone()).isEqualTo("0901234567");
            assertThat(result.avatar()).isEqualTo(account.getAvatarUrl());
            assertThat(result.role()).isEqualTo("STAFF");
            assertThat(result.createdAt()).isEqualTo(account.getCreatedAt());
            assertThat(result.updatedAt()).isEqualTo(employee.getUpdatedAt());
            assertThat(result.toString()).doesNotContain("private-password-hash");
        }
        employee.setAvatarUrl("https://example.com/profile.png");
        assertThat(mapper.toResponse(employee, now).avatar()).isEqualTo(employee.getAvatarUrl());
        employee.setAvatarUrl(" ");
        employee.setFullName(null);
        employee.setPhone(null);
        var incomplete = mapper.toResponse(employee, now);
        assertThat(incomplete.name()).isNull();
        assertThat(incomplete.phone()).isNull();
        assertThat(incomplete.avatar()).isEqualTo(account.getAvatarUrl());
    }
}
