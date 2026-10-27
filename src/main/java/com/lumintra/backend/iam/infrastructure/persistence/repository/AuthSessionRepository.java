package com.lumintra.backend.iam.infrastructure.persistence.repository;

import com.lumintra.backend.iam.infrastructure.persistence.entity.AuthSessionEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {
    boolean existsByIdAndAccount_AccountIdAndRevokedFalseAndExpiresAtAfter(String id, Long accountId, Instant now);

    @Modifying
    @Query("update AuthSessionEntity s set s.revoked = true where s.account.accountId = :accountId")
    int revokeAll(@Param("accountId") Long accountId);
}
