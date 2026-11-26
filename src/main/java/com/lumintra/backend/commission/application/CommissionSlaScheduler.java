package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.application.service.CommissionSlaWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.BiPredicate;

/**
 * Commission time-based worker, run every {@code app.commission.sla-check-interval-ms} (default 60s).
 *
 * <ul>
 *   <li>CMS-28 (#133) AC01: a commission left in AWAITING_ESTIMATE past its 24h estimate window gets the
 *       default 24h estimate ({@code SYSTEM_DEFAULT}) and moves to IN_PROGRESS.</li>
 *   <li>CMS-27 (#132): a PREVIEW_DELIVERED commission past {@code auto_complete_at} is completed as
 *       {@code AUTO_COMPLETED}, exactly as if the customer had approved the delivered preview.</li>
 *   <li>CMS-28 (#133) AC02: commissions in IN_PROGRESS, INTERNAL_REVIEW or INTERNAL_REVISION_REQUIRED past
 *       {@code sla_due_at} get {@code is_sla_overdue = true}.</li>
 * </ul>
 *
 * Each default estimate and each auto-completion runs in its own transaction, so one failing commission
 * does not block the rest.
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
        Tally estimates = process("Default revision estimate", worker.findExpiredEstimateWindows(evaluationTime),
                worker::applyDefaultEstimate, evaluationTime);
        Tally completions = process("Auto-completion", worker.findDueAutoCompletions(evaluationTime),
                worker::autoComplete, evaluationTime);
        int flagged = worker.flagOverdue(evaluationTime);
        return new RunResult(estimates.done(), estimates.failed(), completions.done(), completions.failed(), flagged);
    }

    private Tally process(String step, Iterable<Long> commissionIds, BiPredicate<Long, Instant> action, Instant now) {
        int done = 0;
        int failed = 0;
        for (Long commissionId : commissionIds) {
            try {
                if (action.test(commissionId, now)) {
                    done++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn("{} failed [commissionId={}] [reason={}]", step, commissionId, ex.getMessage());
            }
        }
        return new Tally(done, failed);
    }

    private record Tally(int done, int failed) {
    }

    public record RunResult(
            int defaultEstimatesApplied,
            int defaultEstimatesFailed,
            int autoCompleted,
            int autoCompletionsFailed,
            int slaOverdueFlagged) {
    }
}
