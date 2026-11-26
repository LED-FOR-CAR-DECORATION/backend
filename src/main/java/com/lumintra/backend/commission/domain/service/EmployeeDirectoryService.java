package com.lumintra.backend.commission.domain.service;

/**
 * Domain port for verifying employee directory roles and availability.
 */
public interface EmployeeDirectoryService {

    /**
     * Checks if the employee exists, is active, and possesses the DESIGNER role.
     *
     * @param employeeId target employee ID
     * @return true if target is an active DESIGNER, false otherwise
     */
    boolean isDesigner(Long employeeId);

    /**
     * Checks if the employee exists, is active, and possesses the STAFF role.
     *
     * @param employeeId target employee ID
     * @return true if target is an active STAFF member, false otherwise
     */
    boolean isStaff(Long employeeId);
}
