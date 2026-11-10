package com.lumintra.backend.device.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "led_model")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedModelEntity {

    @Id
    @Column(name = "model_id")
    private Long modelId;

    @Column(name = "model_name", nullable = false, length = 255)
    private String modelName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spec")
    private Map<String, Object> spec = new HashMap<>();

    @Column(name = "stock_quantity", nullable = false)
    private Long stockQuantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static LedModelEntity create(Long modelId, String modelName, Map<String, Object> spec, Long stockQuantity) {
        LedModelEntity entity = new LedModelEntity();
        entity.setModelId(modelId);
        entity.setModelName(modelName);
        entity.setSpec(spec != null ? spec : new HashMap<>());
        entity.setStockQuantity(stockQuantity != null ? stockQuantity : 0L);
        return entity;
    }

    public boolean hasSufficientStock(long requestedQuantity) {
        return requestedQuantity > 0 && stockQuantity != null && stockQuantity >= requestedQuantity;
    }
}
