package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.application.service.CommissionSlaWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommissionSlaSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");

    private final CommissionSlaWorker worker = mock(CommissionSlaWorker.class);
    private final CommissionSlaScheduler scheduler = new CommissionSlaScheduler(worker);

    @Test
    @DisplayName("AC01+AC02 [N]: applies default estimates, keeps going past failures, then flags overdue SLAs")
    void appliesEstimatesThenFlagsOverdue() {
        when(worker.findExpiredEstimateWindows(NOW)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(worker.applyDefaultEstimate(1L, NOW)).thenReturn(true);
        when(worker.applyDefaultEstimate(2L, NOW)).thenThrow(new IllegalStateException("no confirmed revision"));
        when(worker.applyDefaultEstimate(3L, NOW)).thenReturn(false);
        when(worker.applyDefaultEstimate(4L, NOW)).thenReturn(true);
        when(worker.flagOverdue(NOW)).thenReturn(5);

        CommissionSlaScheduler.RunResult result = scheduler.runAt(NOW);

        assertThat(result).isEqualTo(new CommissionSlaScheduler.RunResult(2, 1, 5));
        InOrder order = inOrder(worker);
        order.verify(worker).findExpiredEstimateWindows(NOW);
        order.verify(worker).applyDefaultEstimate(4L, NOW);
        order.verify(worker).flagOverdue(NOW);
    }

    @Test
    @DisplayName("AC02 [N]: with no expired windows only the overdue flagging runs")
    void flagsWhenNothingToEstimate() {
        when(worker.findExpiredEstimateWindows(NOW)).thenReturn(List.of());

        assertThat(scheduler.runAt(NOW)).isEqualTo(new CommissionSlaScheduler.RunResult(0, 0, 0));
        verify(worker).flagOverdue(NOW);
    }

    @Test
    @DisplayName("[B]: a null evaluation time falls back to the current time")
    void nullTimeUsesNow() {
        when(worker.findExpiredEstimateWindows(any())).thenReturn(List.of());

        scheduler.runAt(null);

        verify(worker).flagOverdue(any(Instant.class));
    }
}
