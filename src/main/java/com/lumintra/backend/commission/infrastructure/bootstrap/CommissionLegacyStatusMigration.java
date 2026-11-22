package com.lumintra.backend.commission.infrastructure.bootstrap;

import com.lumintra.backend.commission.domain.model.CommissionRevisionEstimatePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/** Upgrades pre-revision-request records before Hibernate tries to decode their removed enum value. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class CommissionLegacyStatusMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // A durable confirmed revision can enter the new estimate flow. Older records have no
        // revision request to estimate, so resume work while preserving their existing deadline.
        int awaitingEstimate = jdbc.update("""
                UPDATE commission SET status = 'AWAITING_ESTIMATE',
                    sla_due_at = ?, is_sla_overdue = false
                WHERE status = 'CUSTOMER_REVISION_REQUESTED'
                  AND EXISTS (SELECT 1 FROM commission_revision_request r
                              WHERE r.commission_id = commission.commission_id AND r.status = 'CONFIRMED')
                """, Timestamp.from(Instant.now().plus(CommissionRevisionEstimatePolicy.ESTIMATE_WINDOW)));
        int resumed = jdbc.update("""
                UPDATE commission SET status = 'IN_PROGRESS'
                WHERE status = 'CUSTOMER_REVISION_REQUESTED'
                """);
        if (awaitingEstimate + resumed > 0) {
            log.info("Migrated legacy commission statuses [awaitingEstimate={}, resumedWork={}]", awaitingEstimate, resumed);
        }
    }
}
