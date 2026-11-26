package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LedModelRepository extends JpaRepository<LedModelEntity, Long>, JpaSpecificationExecutor<LedModelEntity> {

    boolean existsByModelNameIgnoreCase(String modelName);

    /** Row lock serialising firmware uploads per model (one ACTIVE release, increasing versions). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM LedModelEntity m WHERE m.modelId = :modelId")
    Optional<LedModelEntity> findByIdForUpdate(@Param("modelId") Long modelId);

    Optional<LedModelEntity> findByProductId(Long productId);

    boolean existsByModelNameIgnoreCaseAndModelIdNot(String modelName, Long modelId);

    @Modifying
    @Query("""
            UPDATE LedModelEntity m
            SET m.stockQuantity = m.stockQuantity - :quantity,
                m.updatedAt = instant
            WHERE m.modelId = :modelId
              AND m.stockQuantity >= :quantity
            """)
    int deductStockIfAvailable(@Param("modelId") Long modelId, @Param("quantity") long quantity);

    @Modifying
    @Query("""
            UPDATE LedModelEntity m
            SET m.stockQuantity = m.stockQuantity + :quantity,
                m.updatedAt = instant
            WHERE m.modelId = :modelId
            """)
    int restoreStock(@Param("modelId") Long modelId, @Param("quantity") long quantity);
}
