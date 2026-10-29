package com.lumintra.backend.iam.infrastructure.persistence.repository;

import com.lumintra.backend.iam.domain.model.OtpPurpose;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserOtpTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserOtpTokenRepository extends JpaRepository<UserOtpTokenEntity, Long> {
    Optional<UserOtpTokenEntity> findByEmailAndPurpose(String email, OtpPurpose purpose);
    void deleteByEmailAndPurpose(String email, OtpPurpose purpose);
}