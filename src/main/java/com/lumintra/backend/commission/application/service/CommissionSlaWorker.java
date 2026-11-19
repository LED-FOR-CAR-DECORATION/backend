package com.lumintra.backend.commission.application.service;

import com.lumintra.backend.commission.domain.model.CommissionRevisionEstimatePolicy;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.RevisionEstimateSource;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Transactional units of the CMS-28 estimate and SLA job, called by {@code CommissionSlaScheduler}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionSlaWorker {

    /** Statuses in which the designer owes the next deliverable, so a passed {@code sla_due_at} is overdue. */
    public static final Set<CommissionStatus> SLA_TRACKED_STATUSES = EnumSet.of(
            CommissionStatus.IN_PROGRESS,
            CommissionStatus.INTERNAL_REVIEW,
            CommissionStatus.INTERNAL_REVISION_REQUIRED);

    private final CommissionRequestRepository commissions;
    private final CommissionRevisionEstimateService estimates;

    /** AWAITING_ESTIMATE commissions whose 24h estimate window has closed, oldest first. */
    @Transactional(readOnly = true)
    public List<Long> findExpiredEstimateWindows(Instant now) {
        return commissions.findIdsWithSlaDueAtOrBefore(CommissionStatus.AWAITING_ESTIMATE, now);
    }

    /**
     * AC01: applies the default 24h estimate to one commission whose estimate window has closed, moving it
     * from AWAITING_ESTIMATE to IN_PROGRESS. Runs in its own transaction under the commission row lock so a
     * designer estimate submitted at the same moment wins or loses cleanly.
     *
     * @return {@code true} when the default estimate was applied, {@code false} when the commission no longer
     *         needs it (the designer estimated meanwhile, or the deadline moved)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean applyDefaultEstimate(Long commissionId, Instant now) {
        CommissionRequestEntity commission = commissions.findByIdForUpdate(commissionId).orElse(null);
        if (commission == null
                || commission.getStatus() != CommissionStatus.AWAITING_ESTIMATE
                || commission.getSlaDueAt() == null
                || commission.getSlaDueAt().isAfter(now)) {
            return false;
        }
        estimates.apply(commission, CommissionRevisionEstimatePolicy.DEFAULT_ESTIMATE_HOURS,
                RevisionEstimateSource.SYSTEM_DEFAULT, now);
        log.info("Default revision estimate applied [commissionId={}] [estimatedHours={}] [estimateWindowClosedAt={}]",
                commissionId, CommissionRevisionEstimatePolicy.DEFAULT_ESTIMATE_HOURS, commission.getSlaDueAt());
        return true;
    }

    /** AC02: flags {@code is_sla_overdue} on SLA-tracked commissions past {@code sla_due_at}. */
    @Transactional
    public int flagOverdue(Instant now) {
        int flagged = commissions.flagSlaOverdue(SLA_TRACKED_STATUSES, now);
        if (flagged > 0) {
            log.info("Commission SLA overdue flagged [count={}] [evaluatedAt={}]", flagged, now);
        }
        return flagged;
    }
}
