package com.lumintra.backend.monitoring.infrastructure.persistence.repository;

import com.lumintra.backend.monitoring.infrastructure.persistence.entity.ApiMetricBucketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiMetricBucketRepository extends JpaRepository<ApiMetricBucketEntity, Long> {
}
