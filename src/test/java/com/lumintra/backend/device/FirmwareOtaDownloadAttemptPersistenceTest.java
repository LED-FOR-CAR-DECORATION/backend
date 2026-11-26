package com.lumintra.backend.device;

import com.lumintra.backend.device.domain.model.OtaSessionStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareOtaSessionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareOtaSessionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ota_download_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@ContextConfiguration(classes = FirmwareOtaDownloadAttemptPersistenceTest.Config.class)
class FirmwareOtaDownloadAttemptPersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = LedModelEntity.class)
    @EnableJpaRepositories(basePackageClasses = LedModelRepository.class)
    static class Config {}

    @Autowired private FirmwareOtaSessionRepository sessionRepository;

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private FirmwareOtaSessionEntity save(String hashChar, Instant expiresAt) {
        return sessionRepository.saveAndFlush(FirmwareOtaSessionEntity.issue(hashChar.repeat(64), 1L, 12L,
                "0".repeat(32), expiresAt));
    }

    private int claim(Long id, Instant at) {
        return sessionRepository.claimDownloadAttempt(id, OtaSessionStatus.ISSUED, 3, at);
    }

    @Test
    @DisplayName("DEV-23 Persistence [N]: Three attempts are granted, the fourth refused; first attempt time is kept")
    void claim_allowsThreeAttempts() {
        Long id = save("a", NOW.plusSeconds(600)).getSessionId();

        assertThat(claim(id, NOW)).isEqualTo(1);
        assertThat(claim(id, NOW.plusSeconds(10))).isEqualTo(1);
        assertThat(claim(id, NOW.plusSeconds(20))).isEqualTo(1);
        assertThat(claim(id, NOW.plusSeconds(30))).isZero();

        FirmwareOtaSessionEntity reloaded = sessionRepository.findById(id).orElseThrow();
        assertThat(reloaded.getDownloadAttempts()).isEqualTo(3);
        assertThat(reloaded.getDownloadStartedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("DEV-23 Persistence [A]: Expired or revoked sessions are refused and left unchanged")
    void claim_refusesExpiredOrRevoked() {
        Long expired = save("b", NOW.minusSeconds(1)).getSessionId();
        FirmwareOtaSessionEntity revoked = FirmwareOtaSessionEntity.issue("c".repeat(64), 2L, 12L, "0".repeat(32),
                NOW.plusSeconds(600));
        revoked.revoke();
        Long revokedId = sessionRepository.saveAndFlush(revoked).getSessionId();

        assertThat(claim(expired, NOW)).isZero();
        assertThat(claim(revokedId, NOW)).isZero();
        assertThat(sessionRepository.findById(expired).orElseThrow().getDownloadAttempts()).isZero();
        assertThat(sessionRepository.findById(revokedId).orElseThrow().getDownloadStartedAt()).isNull();
    }
}
