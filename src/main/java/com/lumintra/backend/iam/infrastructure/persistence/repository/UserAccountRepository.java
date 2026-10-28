package com.lumintra.backend.iam.infrastructure.persistence.repository;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface UserAccountRepository extends JpaRepository<UserAccountEntity,Long> {
    Optional<UserAccountEntity> findByEmail(String email);
    Optional<UserAccountEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from UserAccountEntity a where a.accountId=:id")
    Optional<UserAccountEntity> lockById(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from UserAccountEntity a where lower(a.email)=lower(:email)")
    Optional<UserAccountEntity> lockByEmail(@Param("email") String email);
}
