package com.lumintra.backend.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumintra.backend.device.api.response.FirmwareReleaseSummary;
import com.lumintra.backend.device.application.ListFirmwareReleasesUseCase;
import com.lumintra.backend.device.domain.model.FirmwareReleaseStatus;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class ListFirmwareReleasesUseCaseTest {
    @Test
    void filtersByModelAndStatusSeparatelyAndTogether() {
        var db =
                new EmbeddedDatabaseBuilder()
                        .generateUniqueName(true)
                        .setType(EmbeddedDatabaseType.H2)
                        .build();
        try {
            var jdbc = JdbcClient.create(db);
            jdbc.sql(
                            """
CREATE TABLE firmware_version (
    firmware_id bigint PRIMARY KEY, model_id bigint NOT NULL,
    version_name varchar(100), status varchar(30), release_notes varchar(255),
    released_at timestamp with time zone
)
""")
                    .update();
            jdbc.sql(
                            """
                            INSERT INTO firmware_version VALUES
                                (1, 10, '1.0', 'SUPERSEDED', NULL, NULL),
                                (2, 10, '2.0', 'ACTIVE', 'New release', NULL),
                                (3, 20, '1.0', 'ACTIVE', NULL, NULL),
                                (4, 20, '0.9', 'DEPRECATED', NULL, NULL)
                            """)
                    .update();
            var useCase = new ListFirmwareReleasesUseCase(jdbc);
            assertThat(useCase.execute(null, null)).hasSize(4);
            assertThat(useCase.execute(10L, null))
                    .extracting(FirmwareReleaseSummary::firmwareId)
                    .containsExactly(2L, 1L);
            assertThat(useCase.execute(null, FirmwareReleaseStatus.ACTIVE))
                    .extracting(FirmwareReleaseSummary::firmwareId)
                    .containsExactly(3L, 2L);
            assertThat(useCase.execute(10L, FirmwareReleaseStatus.ACTIVE))
                    .extracting(FirmwareReleaseSummary::firmwareId)
                    .containsExactly(2L);
            assertThat(useCase.execute(10L, FirmwareReleaseStatus.DEPRECATED)).isEmpty();
        } finally {
            db.shutdown();
        }
    }
}
