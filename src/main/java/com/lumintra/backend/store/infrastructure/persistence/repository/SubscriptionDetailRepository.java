package com.lumintra.backend.store.infrastructure.persistence.repository;

import com.lumintra.backend.store.infrastructure.persistence.entity.SubscriptionDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionDetailRepository extends JpaRepository<SubscriptionDetailEntity, Long> {
    Optional<SubscriptionDetailEntity> findByProductId(Long productId);

    Optional<SubscriptionDetailEntity> findByPlanCode(String planCode);
}
