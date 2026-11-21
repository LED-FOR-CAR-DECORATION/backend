package com.lumintra.backend.commission;

import com.lumintra.backend.commission.application.service.CommissionSlaWorker;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        properties = {
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@ActiveProfiles("test")
@ContextConfiguration(classes = CommissionSlaRepositoryQueryTest.Config.class)
class CommissionSlaRepositoryQueryTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = CommissionRequestEntity.class)
    @EnableJpaRepositories(basePackageClasses = CommissionRequestRepository.class)
    static class Config {}

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CommissionRequestRepository repository;

    private CommissionRequestEntity commission(CommissionStatus status, Instant slaDueAt, boolean overdue) {
        CommissionRequestEntity entity = CommissionRequestEntity.createPendingReview(
                7L, "Logo", "Animated logo", "CUSTOM", "32x64", "{}",
                new BigDecimal("100000.00"), 5, 3L);
        entity.setStatus(status);
        entity.setSlaDueAt(slaDueAt);
        entity.setSlaOverdue(overdue);
        return repository.save(entity);
    }

    @Test
    @DisplayName("AC01: finds AWAITING_ESTIMATE commissions whose estimate window closed, oldest first")
    void findsExpiredEstimateWindowsOldestFirst() {
        CommissionRequestEntity older = commission(CommissionStatus.AWAITING_ESTIMATE, NOW.minus(Duration.ofHours(3)), false);
        CommissionRequestEntity exact = commission(CommissionStatus.AWAITING_ESTIMATE, NOW, false);
        commission(CommissionStatus.AWAITING_ESTIMATE, NOW.plusSeconds(1), false);
        commission(CommissionStatus.AWAITING_ESTIMATE, null, false);
        commission(CommissionStatus.IN_PROGRESS, NOW.minus(Duration.ofHours(5)), false);
        entityManager.flush();

        assertThat(repository.findIdsWithSlaDueAtOrBefore(CommissionStatus.AWAITING_ESTIMATE, NOW))
                .containsExactly(older.getCommissionId(), exact.getCommissionId());
    }

    @Test
    @DisplayName("AC02: flags only tracked statuses strictly past sla_due_at that are not yet flagged")
    void flagsOnlyTrackedOverdueCommissions() {
        CommissionRequestEntity inProgress = commission(CommissionStatus.IN_PROGRESS, NOW.minusSeconds(1), false);
        CommissionRequestEntity review = commission(CommissionStatus.INTERNAL_REVIEW, NOW.minus(Duration.ofHours(2)), false);
        CommissionRequestEntity revision = commission(CommissionStatus.INTERNAL_REVISION_REQUIRED, NOW.minus(Duration.ofDays(1)), false);
        CommissionRequestEntity notYetDue = commission(CommissionStatus.IN_PROGRESS, NOW, false);
        CommissionRequestEntity awaitingEstimate = commission(CommissionStatus.AWAITING_ESTIMATE, NOW.minusSeconds(60), false);
        CommissionRequestEntity delivered = commission(CommissionStatus.PREVIEW_DELIVERED, NOW.minusSeconds(60), false);
        CommissionRequestEntity alreadyFlagged = commission(CommissionStatus.IN_PROGRESS, NOW.minusSeconds(60), true);
        CommissionRequestEntity noSla = commission(CommissionStatus.IN_PROGRESS, null, false);
        entityManager.flush();

        int flagged = repository.flagSlaOverdue(CommissionSlaWorker.SLA_TRACKED_STATUSES, NOW);

        assertThat(flagged).isEqualTo(3);
        assertThat(reload(inProgress).isSlaOverdue()).isTrue();
        assertThat(reload(review).isSlaOverdue()).isTrue();
        assertThat(reload(revision).isSlaOverdue()).isTrue();
        assertThat(reload(inProgress).getUpdatedAt()).isEqualTo(NOW);
        assertThat(reload(notYetDue).isSlaOverdue()).isFalse();
        assertThat(reload(awaitingEstimate).isSlaOverdue()).isFalse();
        assertThat(reload(delivered).isSlaOverdue()).isFalse();
        assertThat(reload(alreadyFlagged).isSlaOverdue()).isTrue();
        assertThat(reload(noSla).isSlaOverdue()).isFalse();

        assertThat(repository.flagSlaOverdue(CommissionSlaWorker.SLA_TRACKED_STATUSES, NOW)).isZero();
    }

    @Test
    @DisplayName("CMS-27 AC01: finds PREVIEW_DELIVERED commissions past auto_complete_at (inclusive), oldest first")
    void findsCommissionsDueForAutoCompletion() {
        CommissionRequestEntity older = delivered(CommissionStatus.PREVIEW_DELIVERED, NOW.minus(Duration.ofDays(1)));
        CommissionRequestEntity exact = delivered(CommissionStatus.PREVIEW_DELIVERED, NOW);
        delivered(CommissionStatus.PREVIEW_DELIVERED, NOW.plusSeconds(1));
        delivered(CommissionStatus.PREVIEW_DELIVERED, null);
        delivered(CommissionStatus.AWAITING_EXTRA_PAYMENT, NOW.minus(Duration.ofDays(2)));
        delivered(CommissionStatus.COMPLETED, NOW.minus(Duration.ofDays(3)));
        entityManager.flush();

        assertThat(repository.findIdsDueForAutoCompletion(NOW))
                .containsExactly(older.getCommissionId(), exact.getCommissionId());
    }

    private CommissionRequestEntity delivered(CommissionStatus status, Instant autoCompleteAt) {
        CommissionRequestEntity entity = commission(status, null, false);
        entity.setAutoCompleteAt(autoCompleteAt);
        return repository.save(entity);
    }

    private CommissionRequestEntity reload(CommissionRequestEntity entity) {
        return repository.findById(entity.getCommissionId()).orElseThrow();
    }
}
