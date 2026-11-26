package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.RejectCommissionRequest;
import com.lumintra.backend.commission.api.response.RejectCommissionResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionStaffLockException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RejectCommissionRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(RejectCommissionRequestUseCase.class);

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionRequestMapper commissionRequestMapper;

    @Transactional
    public RejectCommissionResponse execute(Long commissionId, RejectCommissionRequest request, Long callerStaffId, boolean isPrivilegedManagement) {
        log.info("Processing commission rejection request [commissionId={}] [callerStaffId={}] [isPrivileged={}]",
                commissionId, callerStaffId, isPrivilegedManagement);

        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId)
                .orElseThrow(() -> new CommissionNotFoundException("Commission request not found with id: " + commissionId));

        // AC01: Restrict access to STAFF role and enforce assigned_staff_id lock
        if (commission.getAssignedStaffId() != null
                && !commission.getAssignedStaffId().equals(callerStaffId)
                && !isPrivilegedManagement) {
            log.warn("Commission staff lock violation during rejection [commissionId={}] [assignedStaffId={}] [callerStaffId={}]",
                    commissionId, commission.getAssignedStaffId(), callerStaffId);
            throw new CommissionStaffLockException(
                    "Commission request " + commissionId + " is locked by assigned staff " + commission.getAssignedStaffId()
            );
        }

        // AC02: Provide rejection reason and transition PENDING_REVIEW to CANCELLED
        if (commission.getStatus() != CommissionStatus.PENDING_REVIEW) {
            log.warn("Invalid commission status for rejection [commissionId={}] [currentStatus={}]",
                    commissionId, commission.getStatus());
            throw new InvalidCommissionStatusTransitionException(
                    "Cannot reject commission in status " + commission.getStatus() + " (must be PENDING_REVIEW)"
            );
        }

        // Auto-assign staff if not already assigned
        if (commission.getAssignedStaffId() == null) {
            commission.setAssignedStaffId(callerStaffId);
        }

        commission.setRejectionReason(request.rejectionReason().trim());
        commission.setStatus(CommissionStatus.CANCELLED);

        CommissionRequestEntity saved = commissionRequestRepository.save(commission);

        log.info("Commission rejected successfully [commissionId={}] [assignedStaffId={}] [status={}] [rejectionReason={}]",
                saved.getCommissionId(), saved.getAssignedStaffId(), saved.getStatus(), saved.getRejectionReason());

        RejectCommissionResponse response = commissionRequestMapper.toRejectResponse(saved);
        if (response.updatedAt() == null) {
            response = RejectCommissionResponse.builder()
                    .commissionId(response.commissionId())
                    .status(response.status())
                    .assignedStaffId(response.assignedStaffId())
                    .rejectionReason(response.rejectionReason())
                    .updatedAt(Instant.now())
                    .build();
        }
        return response;
    }
}
