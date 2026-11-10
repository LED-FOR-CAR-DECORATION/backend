package com.lumintra.backend.iam.infrastructure.persistence.mapper;

import com.lumintra.backend.iam.api.response.ManagedUserListResponse.UserItem;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ManagedUserMapper {
    public UserItem toItem(EmployeeEntity employee) {
        var account = employee.getAccount();
        String avatar = employee.getAvatarUrl();
        if (avatar == null || avatar.isBlank()) avatar = account.getAvatarUrl();
        return new UserItem(account.getAccountId(), employee.getFullName(), account.getEmail(),
                employee.getPhone(), avatar, List.of(employee.getRole().getRoleName().toUpperCase(Locale.ROOT)),
                account.getStatus(), account.getStatus() == AccountStatus.ACTIVE);
    }
}
