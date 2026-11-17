package com.lumintra.backend.iam.infrastructure.persistence;

import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeLookupServiceImpl implements EmployeeLookupService {

    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeProfileSnapshot> findProfileByEmployeeId(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(findProfilesByEmployeeIds(Set.of(employeeId)).get(employeeId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, EmployeeProfileSnapshot> findProfilesByEmployeeIds(Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> validIds = employeeIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (validIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return employeeRepository.findProfileSnapshotsByEmployeeIds(validIds).stream()
                .collect(Collectors.toMap(
                        EmployeeProfileSnapshot::employeeId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<Long> findActiveStaffEmployeeIds() {
        return employeeRepository.findActiveStaffEmployeeIds();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveDesigner(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            return false;
        }
        return employeeRepository.isActiveDesigner(employeeId);
    }
}
