package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.InputRevisionEstimateRequest;
import com.lumintra.backend.commission.api.response.InputRevisionEstimateResponse;
import com.lumintra.backend.commission.application.service.CommissionRevisionEstimateService;
import com.lumintra.backend.commission.domain.model.RevisionEstimateSource;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/** CMS-24: the assigned designer estimates a confirmed revision and starts work on it. */
@Service
@RequiredArgsConstructor
public class InputRevisionEstimateUseCase {

    private final CommissionRequestRepository commissions;
    private final CommissionRevisionEstimateService estimates;

    @Transactional
    public InputRevisionEstimateResponse execute(Long commissionId, Long designerId, InputRevisionEstimateRequest request) {
        // Row lock: the default-estimate scheduler (CMS-28) may act on the same commission.
        var commission = commissions.findByIdForUpdate(commissionId)
                .orElseThrow(() -> new CommissionNotFoundException("Commission request not found."));
        // AC01: assigned designer only
        if (designerId == null || !Objects.equals(designerId, commission.getAssignedDesignerId())) {
            throw new CommissionOwnershipMismatchException(
                    "Only the assigned designer can estimate this revision.");
        }
        // AC02: set sla_due_at and move AWAITING_ESTIMATE to IN_PROGRESS
        var applied = estimates.apply(
                commission, request.estimatedHours(), RevisionEstimateSource.DESIGNER, Instant.now());
        var revision = applied.revision();
        return InputRevisionEstimateResponse.builder()
                .commissionId(applied.commission().getCommissionId())
                .status(applied.commission().getStatus())
                .revisionRequestId(revision.getRevisionRequestId())
                .revisionNumber(revision.getRevisionNumber())
                .estimatedHours(revision.getEstimatedHours())
                .estimateSource(revision.getEstimateSource())
                .slaDueAt(applied.commission().getSlaDueAt())
                .customerDeliveryDeadline(revision.getCustomerDueAt())
                .estimatedAt(revision.getEstimatedAt())
                .build();
    }
}
