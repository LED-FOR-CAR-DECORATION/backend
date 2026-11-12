package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LedModelRepository extends JpaRepository<LedModelEntity, Long> {

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
