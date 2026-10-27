package com.lumintra.backend.iam.infrastructure.persistence.repository;

import com.lumintra.backend.iam.infrastructure.persistence.entity.GoogleIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleIdentityRepository extends JpaRepository<GoogleIdentityEntity, String> {
    boolean existsByAccount_AccountId(Long accountId);
}
