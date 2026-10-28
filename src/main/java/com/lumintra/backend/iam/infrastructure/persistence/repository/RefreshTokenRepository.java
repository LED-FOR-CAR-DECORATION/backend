package com.lumintra.backend.iam.infrastructure.persistence.repository;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity,Long> {
    boolean existsByRefreshTokenIdAndAccount_AccountIdAndRevokedAtIsNullAndExpiresAtAfter(Long id,Long accountId,Instant now);
    @Modifying @Query("update RefreshTokenEntity r set r.revokedAt=:now where r.account.accountId=:accountId and r.revokedAt is null")
    int revokeAll(@Param("accountId") Long accountId,@Param("now") Instant now);
}
