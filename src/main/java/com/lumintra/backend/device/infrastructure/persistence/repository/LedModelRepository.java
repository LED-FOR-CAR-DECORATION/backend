package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedModelRepository extends JpaRepository<LedModelEntity, Long> {
}
