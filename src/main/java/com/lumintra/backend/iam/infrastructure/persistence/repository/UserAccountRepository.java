package com.lumintra.backend.iam.infrastructure.persistence.repository;

import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccountEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from UserAccountEntity a where a.accountId = :id")
    Optional<UserAccountEntity> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from UserAccountEntity a where lower(a.email) = lower(:email)")
    Optional<UserAccountEntity> lockByEmail(@org.springframework.data.repository.query.Param("email") String email);
    Optional<UserAccountEntity> findByEmail(String email);
    Optional<UserAccountEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
