package com.lumintra.backend.store.infrastructure.persistence.repository;

import com.lumintra.backend.store.infrastructure.persistence.entity.BundleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

@Repository
public interface BundleRepository extends JpaRepository<BundleEntity, Long> {
    Optional<BundleEntity> findByProductId(Long productId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BundleEntity> findByBundleId(Long bundleId);
}
