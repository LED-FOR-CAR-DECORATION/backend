package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.AssignCommissionRequest;
import com.lumintra.backend.commission.api.response.AssignCommissionResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.EmployeeDirectoryService;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionStaffLockException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import com.lumintra.backend.shared.exception.commission.InvalidDesignerAssignmentException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AssignCommissionToDesignerUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssignCommissionToDesignerUseCase.class);
    public static final Duration DEFAULT_DRAFT_SLA = Duration.ofHours(72);

    private final CommissionRequestRepository commissionRequestRepository;
    private final EmployeeDirectoryService employeeDirectoryService;

    @Transactional
    public AssignCommissionResponse execute(Long commissionId, AssignCommissionRequest request, Long callerStaffId, boolean isPrivilegedManagement) {
        log.info("Processing assign commission request [commissionId={}] [designerId={}] [callerStaffId={}] [isPrivileged={}]",
                commissionId, request.designerId(), callerStaffId, isPrivilegedManagement);

        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId)
                .orElseThrow(() -> new CommissionNotFoundException("Commission request not found with id: " + commissionId));

        // AC01: Restrict access to STAFF role and enforce assigned_staff_id lock
        if (commission.getAssignedStaffId() != null
                && !commission.getAssignedStaffId().equals(callerStaffId)
                && !isPrivilegedManagement) {
            log.warn("Commission staff lock violation during designer assignment [commissionId={}] [assignedStaffId={}] [callerStaffId={}]",
                    commissionId, commission.getAssignedStaffId(), callerStaffId);
            throw new CommissionStaffLockException(
                    "Commission request " + commissionId + " is locked by assigned staff " + commission.getAssignedStaffId()
            );
        }

        // AC03: Transition status from PAID to IN_PROGRESS (verify commission status is PAID)
        if (commission.getStatus() != CommissionStatus.PAID) {
            log.warn("Invalid commission status for designer assignment [commissionId={}] [currentStatus={}]",
                    commissionId, commission.getStatus());
            throw new InvalidCommissionStatusTransitionException(
                    "Cannot assign commission in status " + commission.getStatus() + " to designer (must be PAID)"
            );
        }

        // AC01: Verify target employee has DESIGNER role via EmployeeDirectoryService
        Long designerId = request.designerId();
        if (!employeeDirectoryService.isDesigner(designerId)) {
            log.warn("Invalid designer assignment: target employee is not an active designer [commissionId={}] [designerId={}]",
                    commissionId, designerId);
            throw new InvalidDesignerAssignmentException(
                    "Target employee with id " + designerId + " does not exist, is inactive, or does not have the DESIGNER role"
            );
        }

        // Auto-assign staff if not already assigned
        if (commission.getAssignedStaffId() == null) {
            commission.setAssignedStaffId(callerStaffId);
        }

        // AC02: Set assigned_designer_id and sla_due_at (+72h)
        Instant now = Instant.now();
        Instant slaDueAt = now.plus(DEFAULT_DRAFT_SLA);
        commission.setAssignedDesignerId(designerId);
        commission.setSlaDueAt(slaDueAt);

        // AC03: Transition status from PAID to IN_PROGRESS
        commission.setStatus(CommissionStatus.IN_PROGRESS);

        CommissionRequestEntity saved = commissionRequestRepository.save(commission);

        log.info("Commission assigned to designer [commissionId={}] [assignedDesignerId={}] [slaDueAt={}] [status={}]",
                saved.getCommissionId(), saved.getAssignedDesignerId(), saved.getSlaDueAt(), saved.getStatus());

        return AssignCommissionResponse.builder()
                .commissionId(saved.getCommissionId())
                .status(saved.getStatus())
                .assignedStaffId(saved.getAssignedStaffId())
                .assignedDesignerId(saved.getAssignedDesignerId())
                .slaDueAt(saved.getSlaDueAt())
                .updatedAt(saved.getUpdatedAt() != null ? saved.getUpdatedAt() : now)
                .build();
    }
}
