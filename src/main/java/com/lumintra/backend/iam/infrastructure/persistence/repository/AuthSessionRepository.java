package com.lumintra.backend.iam.infrastructure.persistence.repository;

import com.lumintra.backend.iam.infrastructure.persistence.entity.AuthSessionEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {
    boolean existsByIdAndAccount_AccountIdAndRevokedFalseAndExpiresAtAfter(String id, Long accountId, Instant now);

}
