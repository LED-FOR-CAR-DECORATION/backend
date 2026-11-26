package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.domain.model.OtaSessionStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareOtaSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FirmwareOtaSessionRepository extends JpaRepository<FirmwareOtaSessionEntity, Long> {

    List<FirmwareOtaSessionEntity> findByLedInstanceIdAndStatus(Long ledInstanceId, OtaSessionStatus status);

    /** Device-side lookup for DEV-22/23 by the SHA-256 of the presented token. */
    Optional<FirmwareOtaSessionEntity> findByTokenHash(String tokenHash);

    /**
     * DEV-23: counts one binary download attempt in a single conditional update, so concurrent requests cannot
     * exceed {@code maxAttempts}. Returns 1 when the attempt was granted, 0 when the session is no longer ISSUED,
     * has expired, or has used up its attempts.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update FirmwareOtaSessionEntity s
               set s.downloadAttempts = s.downloadAttempts + 1,
                   s.downloadStartedAt = coalesce(s.downloadStartedAt, :now),
                   s.updatedAt = :now
             where s.sessionId = :sessionId
               and s.status = :status
               and s.expiresAt > :now
               and s.downloadAttempts < :maxAttempts
            """)
    int claimDownloadAttempt(@Param("sessionId") Long sessionId,
                             @Param("status") OtaSessionStatus status,
                             @Param("maxAttempts") int maxAttempts,
                             @Param("now") Instant now);
}
