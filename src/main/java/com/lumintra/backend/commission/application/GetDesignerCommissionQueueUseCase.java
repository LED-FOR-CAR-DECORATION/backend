package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.DesignerCommissionQueueFilterRequest;
import com.lumintra.backend.commission.api.response.DesignerCommissionQueueResponse;
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
public class GetDesignerCommissionQueueUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetDesignerCommissionQueueUseCase.class);

    private final CommissionRequestRepository commissionRepository;
    private final CommissionRequestMapper commissionMapper;
    private final CustomerLookupService customerLookupService;
    private final EmployeeLookupService employeeLookupService;

    public DesignerCommissionQueueResponse execute(Long designerEmployeeId, DesignerCommissionQueueFilterRequest request) {
        DesignerCommissionQueueFilterRequest effectiveFilter = request != null
                ? request
                : new DesignerCommissionQueueFilterRequest();

        Pageable pageable = effectiveFilter.toPageable();

        Page<CommissionRequestEntity> page = effectiveFilter.status() != null
                ? commissionRepository.findAllByAssignedDesignerIdAndStatus(designerEmployeeId, effectiveFilter.status(), pageable)
                : commissionRepository.findAllByAssignedDesignerId(designerEmployeeId, pageable);

        log.info("Designer commission queue retrieved [designerId={}] [status={}] [totalElements={}] [page={}] [size={}]",
                designerEmployeeId, effectiveFilter.status(), page.getTotalElements(), page.getNumber(), page.getSize());

        if (page.isEmpty()) {
            return commissionMapper.toDesignerQueueResponse(page, Collections.emptyMap(), Collections.emptyMap());
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

        return commissionMapper.toDesignerQueueResponse(page, customerProfiles, employeeProfiles);
    }
}
