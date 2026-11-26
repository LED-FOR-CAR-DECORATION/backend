package com.lumintra.backend.commission.application.service;

import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for auto-assigning incoming commission requests to available active STAFF members using Round-Robin scheduling.
 */
@Service
@RequiredArgsConstructor
public class StaffAutoAssignmentService {

    private final EmployeeLookupService employeeLookupService;
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    /**
     * Resolves the next available staff employee ID in round-robin sequence.
     * Returns null if no active staff members are registered.
     */
    public Long assignNextAvailableStaff() {
        List<Long> activeStaffIds = employeeLookupService.findActiveStaffEmployeeIds();
        if (activeStaffIds == null || activeStaffIds.isEmpty()) {
            return null;
        }
        int index = Math.abs(roundRobinCounter.getAndIncrement() % activeStaffIds.size());
        return activeStaffIds.get(index);
    }
}
