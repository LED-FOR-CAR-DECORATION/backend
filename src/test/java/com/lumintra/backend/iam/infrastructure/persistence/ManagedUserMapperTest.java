package com.lumintra.backend.iam.infrastructure.persistence;

import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.mapper.ManagedUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ManagedUserMapperTest {
    EmployeeEntity employee() {
        var account=UserAccountEntity.createCustomerAccount("staff@example.com","private-password-hash");
        account.setAccountId(42L);account.setStatus(AccountStatus.ACTIVE);account.setAvatarUrl("account.png");
        var employee=new EmployeeEntity();employee.setAccount(account);employee.setFullName("Staff");employee.setPhone("0901234567");
        var role=new RoleEntity();role.setRoleName("staff");employee.setRole(role);return employee;
    }

    @Test @DisplayName("N: Map public profile and current role without exposing credential fields")
    void safeMapping() {
        var employee=employee();employee.setAvatarUrl("profile.png");
        var item=new ManagedUserMapper().toItem(employee);
        assertThat(item.accountId()).isEqualTo(42L);assertThat(item.name()).isEqualTo("Staff");
        assertThat(item.roles()).containsExactly("STAFF");assertThat(item.active()).isTrue();
        assertThat(item.phone()).isEqualTo("0901234567");assertThat(item.avatar()).isEqualTo("profile.png");
        assertThat(item.toString()).doesNotContain("private-password-hash");
    }

    @Test @DisplayName("B: Blank profile avatar falls back to account avatar and nullable names are preserved")
    void incompleteProfile() {
        var employee=employee();employee.setAvatarUrl(" ");employee.setFullName(null);employee.setPhone(null);
        var item=new ManagedUserMapper().toItem(employee);
        assertThat(item.avatar()).isEqualTo("account.png");assertThat(item.name()).isNull();assertThat(item.phone()).isNull();
    }

    @Test @DisplayName("N: Active flag follows database lifecycle status")
    void statuses() {
        for(var status:AccountStatus.values()) {
            var employee=employee();employee.getAccount().setStatus(status);
            assertThat(new ManagedUserMapper().toItem(employee).active()).isEqualTo(status==AccountStatus.ACTIVE);
        }
    }
}
