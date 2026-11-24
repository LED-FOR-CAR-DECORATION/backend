package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.domain.model.OtaSessionStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareOtaSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FirmwareOtaSessionRepository extends JpaRepository<FirmwareOtaSessionEntity, Long> {

    List<FirmwareOtaSessionEntity> findByLedInstanceIdAndStatus(Long ledInstanceId, OtaSessionStatus status);

    /** Device-side lookup for DEV-22/23 by the SHA-256 of the presented token. */
    Optional<FirmwareOtaSessionEntity> findByTokenHash(String tokenHash);
}
