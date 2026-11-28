package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.ReassignCommissionRequest;
import com.lumintra.backend.commission.api.response.ReassignCommissionResponse;
import com.lumintra.backend.commission.domain.model.CommissionRevisionEstimatePolicy;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.domain.service.EmployeeDirectoryService;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionReassignmentException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import com.lumintra.backend.shared.exception.commission.InvalidDesignerAssignmentException;
import com.lumintra.backend.shared.exception.commission.InvalidStaffAssignmentException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * CMS-25 (#130): a MANAGER or ADMIN overrides the assigned staff member and/or designer of a commission.
 * The commission status never changes; a newly assigned designer gets a fresh deadline while work is pending.
 */
@Service
@RequiredArgsConstructor
public class ReassignCommissionUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReassignCommissionUseCase.class);

    /** AC03: terminal statuses can't be reassigned at all. */
    static final Set<CommissionStatus> TERMINAL_STATUSES = EnumSet.of(
            CommissionStatus.COMPLETED, CommissionStatus.CANCELLED, CommissionStatus.EXPIRED);

    /** AC03: statuses in which an already-assigned designer can be replaced. */
    static final Set<CommissionStatus> DESIGNER_REASSIGNABLE_STATUSES = EnumSet.of(
            CommissionStatus.IN_PROGRESS,
            CommissionStatus.INTERNAL_REVIEW,
            CommissionStatus.INTERNAL_REVISION_REQUIRED,
            CommissionStatus.PREVIEW_DELIVERED,
            CommissionStatus.AWAITING_EXTRA_PAYMENT,
            CommissionStatus.AWAITING_ESTIMATE);

    private final CommissionRequestRepository commissionRequestRepository;
    private final EmployeeDirectoryService employeeDirectoryService;
    private final CommissionMessageRepository commissionMessageRepository;
    private final CommissionMessageMapper commissionMessageMapper;
    private final CommissionChatBroadcastPort chatBroadcastPort;

    @Transactional
    public ReassignCommissionResponse execute(Long commissionId, ReassignCommissionRequest request, Long callerId) {
        // AC06: row lock so the override can't race with designer or staff actions
        CommissionRequestEntity commission = commissionRequestRepository.findByIdForUpdate(commissionId)
                .orElseThrow(() -> new CommissionNotFoundException("Commission request not found with id: " + commissionId));

        Long newStaffId = request.staffId();
        Long newDesignerId = request.designerId();
        Long oldStaffId = commission.getAssignedStaffId();
        Long oldDesignerId = commission.getAssignedDesignerId();
        CommissionStatus status = commission.getStatus();

        // AC03: allowed statuses
        if (TERMINAL_STATUSES.contains(status)) {
            throw new InvalidCommissionStatusTransitionException(
                    "Cannot reassign commission in status " + status + ".");
        }
        if (newDesignerId != null && (oldDesignerId == null || !DESIGNER_REASSIGNABLE_STATUSES.contains(status))) {
            throw new InvalidCommissionStatusTransitionException(
                    "Designer can only be reassigned once one is assigned (IN_PROGRESS through AWAITING_ESTIMATE); "
                            + "use the assign endpoint for a PAID commission (was " + status + ").");
        }

        // AC04: reassigning to the person already assigned is a no-op request
        if ((newStaffId != null && newStaffId.equals(oldStaffId))
                || (newDesignerId != null && newDesignerId.equals(oldDesignerId))) {
            throw new InvalidCommissionReassignmentException(
                    "The requested employee is already assigned to this commission.");
        }

        // AC02: target roles
        if (newStaffId != null && !employeeDirectoryService.isStaff(newStaffId)) {
            throw new InvalidStaffAssignmentException(
                    "Target employee with id " + newStaffId + " does not exist, is inactive, or does not have the STAFF role");
        }
        if (newDesignerId != null && !employeeDirectoryService.isDesigner(newDesignerId)) {
            throw new InvalidDesignerAssignmentException(
                    "Target employee with id " + newDesignerId + " does not exist, is inactive, or does not have the DESIGNER role");
        }

        Instant now = Instant.now();
        if (newStaffId != null) {
            commission.setAssignedStaffId(newStaffId);
        }
        if (newDesignerId != null) {
            commission.setAssignedDesignerId(newDesignerId);
            resetDesignerDeadline(commission, now);
        }
        CommissionRequestEntity saved = commissionRequestRepository.save(commission);

        // AC06: tell the customer in chat (no names, no reason) — only while the chat is open
        if (status.isChatActive()) {
            CommissionMessageEntity message = commissionMessageRepository.save(
                    CommissionMessageEntity.createSystemMessage(commissionId, chatText(newStaffId != null, newDesignerId != null)));
            chatBroadcastPort.broadcastMessage(commissionId, commissionMessageMapper.toResponse(message));
        }

        log.info("Commission reassigned [commissionId={}] [callerId={}] [status={}] [staff={}->{}] [designer={}->{}]"
                        + " [slaDueAt={}] [reason={}]",
                commissionId, callerId, status, oldStaffId, saved.getAssignedStaffId(), oldDesignerId,
                saved.getAssignedDesignerId(), saved.getSlaDueAt(), request.reason().trim());

        return ReassignCommissionResponse.builder()
                .commissionId(saved.getCommissionId())
                .status(saved.getStatus())
                .assignedStaffId(saved.getAssignedStaffId())
                .assignedDesignerId(saved.getAssignedDesignerId())
                .slaDueAt(saved.getSlaDueAt())
                .isSlaOverdue(saved.isSlaOverdue())
                .updatedAt(saved.getUpdatedAt() != null ? saved.getUpdatedAt() : now)
                .build();
    }

    /**
     * AC05: the new designer gets a fresh deadline and the overdue flag is cleared while they owe work;
     * in other statuses the deadline is left as it is.
     */
    private static void resetDesignerDeadline(CommissionRequestEntity commission, Instant now) {
        switch (commission.getStatus()) {
            case IN_PROGRESS, INTERNAL_REVISION_REQUIRED -> {
                commission.setSlaDueAt(now.plus(AssignCommissionToDesignerUseCase.DEFAULT_DRAFT_SLA));
                commission.setSlaOverdue(false);
            }
            case AWAITING_ESTIMATE -> {
                commission.setSlaDueAt(now.plus(CommissionRevisionEstimatePolicy.ESTIMATE_WINDOW));
                commission.setSlaOverdue(false);
            }
            default -> {
                // INTERNAL_REVIEW, PREVIEW_DELIVERED, AWAITING_EXTRA_PAYMENT: deadline unchanged
            }
        }
    }

    private static String chatText(boolean staffChanged, boolean designerChanged) {
        if (staffChanged && designerChanged) {
            return "Yêu cầu thiết kế này đã được phân công lại cho nhân viên hỗ trợ và nhà thiết kế mới.";
        }
        return designerChanged
                ? "Yêu cầu thiết kế này đã được phân công lại cho nhà thiết kế mới."
                : "Yêu cầu thiết kế này đã được phân công lại cho nhân viên hỗ trợ mới.";
    }
}
