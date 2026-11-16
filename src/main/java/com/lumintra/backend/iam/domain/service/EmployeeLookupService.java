package com.lumintra.backend.iam.domain.service;

import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only domain boundary exposed by IAM for retrieving employee author profiles without leaking persistence internals.
 */
public interface EmployeeLookupService {

    Optional<EmployeeProfileSnapshot> findProfileByEmployeeId(Long employeeId);

    Map<Long, EmployeeProfileSnapshot> findProfilesByEmployeeIds(Collection<Long> employeeIds);
}
