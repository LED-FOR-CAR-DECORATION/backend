package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.application.service.CommissionSlaWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * CMS-28 (issue #133): Default Estimate &amp; SLA Overdue Worker.
 *
 * <ul>
 *   <li>AC01: a commission left in AWAITING_ESTIMATE past its 24h estimate window gets the default 24h
 *       estimate ({@code SYSTEM_DEFAULT}) and moves to IN_PROGRESS.</li>
 *   <li>AC02: commissions in IN_PROGRESS, INTERNAL_REVIEW or INTERNAL_REVISION_REQUIRED past
 *       {@code sla_due_at} get {@code is_sla_overdue = true}.</li>
 * </ul>
 *
 * Each default estimate runs in its own transaction, so one failing commission does not block the rest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommissionSlaScheduler {

    private final CommissionSlaWorker worker;

    @Scheduled(fixedDelayString = "${app.commission.sla-check-interval-ms:60000}")
    public void run() {
        runAt(Instant.now());
    }

    public RunResult runAt(Instant now) {
        Instant evaluationTime = now != null ? now : Instant.now();
        int estimated = 0;
        int failed = 0;
        for (Long commissionId : worker.findExpiredEstimateWindows(evaluationTime)) {
            try {
                if (worker.applyDefaultEstimate(commissionId, evaluationTime)) {
                    estimated++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Default revision estimate failed [commissionId={}] [reason={}]",
                        commissionId, ex.getMessage());
            }
        }
        int flagged = worker.flagOverdue(evaluationTime);
        return new RunResult(estimated, failed, flagged);
    }

    public record RunResult(int defaultEstimatesApplied, int defaultEstimatesFailed, int slaOverdueFlagged) {
    }
}
