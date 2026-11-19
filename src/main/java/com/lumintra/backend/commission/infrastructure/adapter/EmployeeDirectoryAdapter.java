package com.lumintra.backend.commission.infrastructure.adapter;

import com.lumintra.backend.commission.domain.service.EmployeeDirectoryService;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter implementing EmployeeDirectoryService via IAM EmployeeLookupService.
 */
@Component
@RequiredArgsConstructor
public class EmployeeDirectoryAdapter implements EmployeeDirectoryService {

    private final EmployeeLookupService employeeLookupService;

    @Override
    public boolean isDesigner(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            return false;
        }
        return employeeLookupService.isActiveDesigner(employeeId);
    }

    @Override
    public boolean isStaff(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            return false;
        }
        return employeeLookupService.isActiveStaff(employeeId);
    }
}
