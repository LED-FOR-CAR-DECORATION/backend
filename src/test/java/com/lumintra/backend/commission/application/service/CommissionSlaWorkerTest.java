package com.lumintra.backend.commission.application.service;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.RevisionEstimateSource;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommissionSlaWorkerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");

    private CommissionRequestRepository commissions;
    private CommissionRevisionEstimateService estimates;
    private CommissionSlaWorker worker;

    @BeforeEach
    void setUp() {
        commissions = mock(CommissionRequestRepository.class);
        estimates = mock(CommissionRevisionEstimateService.class);
        worker = new CommissionSlaWorker(commissions, estimates);
    }

    private CommissionRequestEntity commission(CommissionStatus status, Instant slaDueAt) {
        CommissionRequestEntity entity = CommissionRequestEntity.builder()
                .commissionId(42L).status(status).slaDueAt(slaDueAt).build();
        when(commissions.findByIdForUpdate(42L)).thenReturn(Optional.of(entity));
        return entity;
    }

    @Test
    @DisplayName("AC01 [N]: closed estimate window gets the default 24h SYSTEM_DEFAULT estimate under the row lock")
    void appliesDefaultEstimateWhenWindowClosed() {
        CommissionRequestEntity entity = commission(CommissionStatus.AWAITING_ESTIMATE, NOW.minus(Duration.ofMinutes(1)));

        assertThat(worker.applyDefaultEstimate(42L, NOW)).isTrue();

        verify(commissions).findByIdForUpdate(42L);
        verify(estimates).apply(entity, 24, RevisionEstimateSource.SYSTEM_DEFAULT, NOW);
    }

    @Test
    @DisplayName("AC01 [B]: window closing exactly now is treated as closed")
    void appliesDefaultEstimateAtExactDeadline() {
        CommissionRequestEntity entity = commission(CommissionStatus.AWAITING_ESTIMATE, NOW);

        assertThat(worker.applyDefaultEstimate(42L, NOW)).isTrue();

        verify(estimates).apply(entity, 24, RevisionEstimateSource.SYSTEM_DEFAULT, NOW);
    }

    @Test
    @DisplayName("AC01 [A]: skips when the designer estimated first (status already IN_PROGRESS)")
    void skipsWhenDesignerAlreadyEstimated() {
        commission(CommissionStatus.IN_PROGRESS, NOW.plus(Duration.ofHours(30)));

        assertThat(worker.applyDefaultEstimate(42L, NOW)).isFalse();

        verify(estimates, never()).apply(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("AC01 [A]: skips when the estimate window is still open after locking")
    void skipsWhenWindowStillOpen() {
        commission(CommissionStatus.AWAITING_ESTIMATE, NOW.plusSeconds(1));

        assertThat(worker.applyDefaultEstimate(42L, NOW)).isFalse();

        verify(estimates, never()).apply(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("AC01 [A]: skips a commission that no longer exists")
    void skipsMissingCommission() {
        when(commissions.findByIdForUpdate(42L)).thenReturn(Optional.empty());

        assertThat(worker.applyDefaultEstimate(42L, NOW)).isFalse();

        verify(estimates, never()).apply(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("AC01 [A]: propagates a missing confirmed revision so the transaction rolls back")
    void propagatesEstimateFailure() {
        CommissionRequestEntity entity = commission(CommissionStatus.AWAITING_ESTIMATE, NOW.minusSeconds(5));
        when(estimates.apply(entity, 24, RevisionEstimateSource.SYSTEM_DEFAULT, NOW))
                .thenThrow(new InvalidCommissionStatusTransitionException("No confirmed revision request is waiting for an estimate."));

        assertThatThrownBy(() -> worker.applyDefaultEstimate(42L, NOW))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
    }

    @Test
    @DisplayName("AC01 [N]: lists expired AWAITING_ESTIMATE windows")
    void findsExpiredWindows() {
        when(commissions.findIdsWithSlaDueAtOrBefore(CommissionStatus.AWAITING_ESTIMATE, NOW)).thenReturn(List.of(1L, 2L));

        assertThat(worker.findExpiredEstimateWindows(NOW)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("AC02 [N]: flags overdue SLAs for designer-owed statuses only")
    void flagsOverdueForTrackedStatuses() {
        when(commissions.flagSlaOverdue(CommissionSlaWorker.SLA_TRACKED_STATUSES, NOW)).thenReturn(3);

        assertThat(worker.flagOverdue(NOW)).isEqualTo(3);
        assertThat(CommissionSlaWorker.SLA_TRACKED_STATUSES).isEqualTo(EnumSet.of(
                CommissionStatus.IN_PROGRESS, CommissionStatus.INTERNAL_REVIEW,
                CommissionStatus.INTERNAL_REVISION_REQUIRED));
    }
}
