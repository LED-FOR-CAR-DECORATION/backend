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
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ota_session_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@ContextConfiguration(classes = FirmwareOtaSessionPersistenceTest.Config.class)
class FirmwareOtaSessionPersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = LedModelEntity.class)
    @EnableJpaRepositories(basePackageClasses = LedModelRepository.class)
    static class Config {}

    @Autowired private FirmwareOtaSessionRepository sessionRepository;
    @Autowired private LedInstanceRepository instanceRepository;

    private FirmwareOtaSessionEntity session(String hashChar, long deviceId) {
        return FirmwareOtaSessionEntity.issue(hashChar.repeat(64), deviceId, 12L, "0".repeat(32),
                Instant.parse("2026-11-23T10:10:00Z"));
    }

    @Test
    @DisplayName("DEV-21 Persistence [N]: Sessions are found by device and status, and by token hash")
    void lookups_byDeviceStatusAndTokenHash() {
        FirmwareOtaSessionEntity open = sessionRepository.save(session("a", 1L));
        FirmwareOtaSessionEntity revoked = session("b", 1L);
        revoked.revoke();
        sessionRepository.save(revoked);
        sessionRepository.save(session("c", 2L));

        assertThat(sessionRepository.findByLedInstanceIdAndStatus(1L, OtaSessionStatus.ISSUED))
                .extracting(FirmwareOtaSessionEntity::getSessionId).containsExactly(open.getSessionId());
        assertThat(sessionRepository.findByTokenHash("a".repeat(64))).get()
                .extracting(FirmwareOtaSessionEntity::getNonce, FirmwareOtaSessionEntity::getDownloadAttempts)
                .containsExactly("0".repeat(32), 0);
        assertThat(sessionRepository.findByTokenHash("d".repeat(64))).isEmpty();
    }

    @Test
    @DisplayName("DEV-21 Persistence [A]: The same token hash cannot be stored twice")
    void tokenHash_isUnique() {
        sessionRepository.saveAndFlush(session("e", 1L));

        assertThatThrownBy(() -> sessionRepository.saveAndFlush(session("e", 2L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DEV-21 Persistence [N]: The device row lock query loads the device")
    void findByIdForUpdate_loadsDevice() {
        LedInstanceEntity device = instanceRepository.save(LedInstanceEntity.createWithState(
                1L, "SN-LOCK", "AA:BB:CC:00:00:99", null, 55L, "Car", LedInstanceStatus.BOUND,
                Instant.now(), Instant.now(), Instant.now()));

        assertThat(instanceRepository.findByIdForUpdate(device.getInstanceId())).get()
                .extracting(LedInstanceEntity::getSerialNumber).isEqualTo("SN-LOCK");
        assertThat(instanceRepository.findByIdForUpdate(-1L)).isEmpty();
    }
}
