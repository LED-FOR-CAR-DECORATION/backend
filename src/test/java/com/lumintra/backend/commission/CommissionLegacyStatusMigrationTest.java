package com.lumintra.backend.commission;

import com.lumintra.backend.commission.infrastructure.bootstrap.CommissionLegacyStatusMigration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionLegacyStatusMigrationTest {
    @Test
    void migratesLegacyRowsWithoutInventingRevisionRecordsAndIsIdempotent() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"));
        jdbc.execute("CREATE TABLE commission (commission_id BIGINT PRIMARY KEY, status VARCHAR(40), sla_due_at TIMESTAMP WITH TIME ZONE, is_sla_overdue BOOLEAN)");
        jdbc.execute("CREATE TABLE commission_revision_request (commission_id BIGINT, status VARCHAR(40))");
        Instant oldDeadline = Instant.parse("2026-11-20T12:00:00Z");
        for (long id = 1; id <= 3; id++) {
            jdbc.update("INSERT INTO commission VALUES (?, 'CUSTOMER_REVISION_REQUESTED', ?, true)", id, Timestamp.from(oldDeadline));
        }
        jdbc.update("INSERT INTO commission VALUES (4, 'COMPLETED', ?, false)", Timestamp.from(oldDeadline));
        jdbc.update("INSERT INTO commission_revision_request VALUES (2, 'CONFIRMED'), (3, 'COMPLETED')");

        var migration = new CommissionLegacyStatusMigration(jdbc);
        Instant before = Instant.now().plusSeconds(86400).minusMillis(1);
        migration.run(null);

        assertThat(jdbc.queryForList("SELECT status FROM commission ORDER BY commission_id", String.class))
                .containsExactly("IN_PROGRESS", "AWAITING_ESTIMATE", "IN_PROGRESS", "COMPLETED");
        assertThat(jdbc.queryForObject("SELECT sla_due_at FROM commission WHERE commission_id=1", Timestamp.class).toInstant())
                .isEqualTo(oldDeadline);
        assertThat(jdbc.queryForObject("SELECT is_sla_overdue FROM commission WHERE commission_id=1", Boolean.class)).isTrue();
        Instant estimateDeadline = jdbc.queryForObject("SELECT sla_due_at FROM commission WHERE commission_id=2", Timestamp.class).toInstant();
        assertThat(estimateDeadline).isBetween(before, Instant.now().plusSeconds(86400).plusMillis(1));
        assertThat(jdbc.queryForObject("SELECT is_sla_overdue FROM commission WHERE commission_id=2", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_revision_request", Integer.class)).isEqualTo(2);

        migration.run(null);
        assertThat(jdbc.queryForObject("SELECT sla_due_at FROM commission WHERE commission_id=2", Timestamp.class).toInstant())
                .isEqualTo(estimateDeadline);
    }
}
