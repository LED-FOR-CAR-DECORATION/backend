package com.lumintra.backend.device;

import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.OtaSessionStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareOtaSessionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareOtaSessionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:installed_firmware_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@ContextConfiguration(classes = InstalledFirmwarePersistenceTest.Config.class)
class InstalledFirmwarePersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = LedModelEntity.class)
    @EnableJpaRepositories(basePackageClasses = LedModelRepository.class)
    static class Config {}

    @Autowired private LedInstanceRepository instanceRepository;
    @Autowired private FirmwareOtaSessionRepository sessionRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    @DisplayName("DEV-10B Persistence [N]: current_firmware_id, firmware_updated_at and a COMPLETED session are stored")
    void installedFirmware_andCompletedSession_persist() {
        Instant at = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        LedInstanceEntity device = instanceRepository.saveAndFlush(LedInstanceEntity.createWithState(
                1L, "SN-FW", "AA:BB:CC:00:00:77", 55L, "Car", LedInstanceStatus.BOUND,
                Instant.now(), Instant.now(), Instant.now()));
        FirmwareOtaSessionEntity session = sessionRepository.saveAndFlush(FirmwareOtaSessionEntity.issue(
                "a".repeat(64), device.getInstanceId(), 12L, "0".repeat(32), at.plusSeconds(600)));

        device.recordInstalledFirmware(12L, at);
        session.complete();
        entityManager.flush();
        entityManager.clear();

        LedInstanceEntity reloaded = instanceRepository.findById(device.getInstanceId()).orElseThrow();
        assertThat(reloaded.getCurrentFirmwareId()).isEqualTo(12L);
        assertThat(reloaded.getFirmwareUpdatedAt()).isEqualTo(at);
        assertThat(sessionRepository.findById(session.getSessionId()).orElseThrow().getStatus())
                .isEqualTo(OtaSessionStatus.COMPLETED);
        assertThat(sessionRepository.findByLedInstanceIdAndStatus(device.getInstanceId(), OtaSessionStatus.ISSUED))
                .isEmpty();
    }
}
