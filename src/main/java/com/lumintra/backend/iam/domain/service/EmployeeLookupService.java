package com.lumintra.backend.iam.domain.service;

import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only domain boundary exposed by IAM for retrieving employee author profiles without leaking persistence internals.
 */
public interface EmployeeLookupService {

    Optional<EmployeeProfileSnapshot> findProfileByEmployeeId(Long employeeId);

    Map<Long, EmployeeProfileSnapshot> findProfilesByEmployeeIds(Collection<Long> employeeIds);

    java.util.List<Long> findActiveStaffEmployeeIds();

    boolean isActiveDesigner(Long employeeId);

    boolean isActiveStaff(Long employeeId);

    java.util.List<EmployeeProfileSnapshot> findActiveDesigners();

    Page<EmployeeProfileSnapshot> findActiveDesigners(String search, Pageable pageable);
}
