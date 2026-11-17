package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.StaffCommissionQueueFilterRequest;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetStaffCommissionQueueUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetStaffCommissionQueueUseCase.class);

    private final CommissionRequestRepository commissionRepository;
    private final CommissionRequestMapper commissionMapper;
    private final CustomerLookupService customerLookupService;
    private final EmployeeLookupService employeeLookupService;

    public StaffCommissionQueueResponse execute(StaffCommissionQueueFilterRequest request) {
        StaffCommissionQueueFilterRequest effectiveFilter = request != null
                ? request
                : new StaffCommissionQueueFilterRequest();

        Pageable pageable = effectiveFilter.toPageable();

        Page<CommissionRequestEntity> page = effectiveFilter.status() != null
                ? commissionRepository.findAllByStatus(effectiveFilter.status(), pageable)
                : commissionRepository.findAll(pageable);

        log.info("Staff commission queue retrieved [status={}] [totalElements={}] [page={}] [size={}]",
                effectiveFilter.status(), page.getTotalElements(), page.getNumber(), page.getSize());

        if (page.isEmpty()) {
            return commissionMapper.toStaffQueueResponse(page, Collections.emptyMap(), Collections.emptyMap());
        }

        Set<Long> customerIds = new HashSet<>();
        Set<Long> employeeIds = new HashSet<>();

        for (CommissionRequestEntity entity : page.getContent()) {
            if (entity.getCustomerId() != null) {
                customerIds.add(entity.getCustomerId());
            }
            if (entity.getAssignedStaffId() != null) {
                employeeIds.add(entity.getAssignedStaffId());
            }
            if (entity.getAssignedDesignerId() != null) {
                employeeIds.add(entity.getAssignedDesignerId());
            }
        }

        Map<Long, CustomerProfileSnapshot> customerProfiles = customerIds.isEmpty()
                ? Collections.emptyMap()
                : customerLookupService.findProfilesByCustomerIds(customerIds);

        Map<Long, EmployeeProfileSnapshot> employeeProfiles = employeeIds.isEmpty()
                ? Collections.emptyMap()
                : employeeLookupService.findProfilesByEmployeeIds(employeeIds);

        return commissionMapper.toStaffQueueResponse(page, customerProfiles, employeeProfiles);
    }
}
