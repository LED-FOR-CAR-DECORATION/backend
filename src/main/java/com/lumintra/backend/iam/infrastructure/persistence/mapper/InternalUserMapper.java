package com.lumintra.backend.iam.infrastructure.persistence.mapper;

import com.lumintra.backend.iam.api.response.CreateInternalUserResponse;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;

import org.springframework.stereotype.Component;

@Component
public class InternalUserMapper {
    public CreateInternalUserResponse toResponse(EmployeeEntity employee) {
        var account = employee.getAccount();
        return new CreateInternalUserResponse(
                account.getAccountId(),
                account.getEmail(),
                employee.getFullName(),
                employee.getRole().getRoleName(),
                account.getStatus());
    }
}
