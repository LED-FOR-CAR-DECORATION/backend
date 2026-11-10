package com.lumintra.backend.store.infrastructure.persistence.repository;

import com.lumintra.backend.store.infrastructure.persistence.entity.BundleItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BundleItemRepository extends JpaRepository<BundleItemEntity, Long> {
    List<BundleItemEntity> findAllByBundle_BundleId(Long bundleId);
}
