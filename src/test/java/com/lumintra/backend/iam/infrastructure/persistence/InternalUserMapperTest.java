package com.lumintra.backend.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import com.lumintra.backend.iam.infrastructure.persistence.entity.*;
import com.lumintra.backend.iam.infrastructure.persistence.mapper.InternalUserMapper;

import org.junit.jupiter.api.*;

class InternalUserMapperTest {
    @Test
    @DisplayName(
            "N: Map safe account and employee fields including an absent name without credentials")
    void safeMapping() {
        var account = UserAccountEntity.createInternalAccount("staff@example.com", "hash");
        account.setAccountId(42L);
        var role = new RoleEntity();
        role.setRoleName("STAFF");
        var employee = EmployeeEntity.createInternal(account, role, null);
        var result = new InternalUserMapper().toResponse(employee);
        assertThat(result.accountId()).isEqualTo(42L);
        assertThat(result.email()).isEqualTo(account.getEmail());
        assertThat(result.name()).isNull();
        assertThat(result.role()).isEqualTo("STAFF");
        assertThat(result.toString()).doesNotContain("hash");
    }
}
