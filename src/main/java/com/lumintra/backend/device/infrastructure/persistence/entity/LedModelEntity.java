package com.lumintra.backend.device.infrastructure.persistence.entity;

import com.lumintra.backend.device.domain.model.LedModelStatus;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity
@Table(name = "led_model")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedModelEntity {

    public static final long LOW_STOCK_THRESHOLD = 5L;
    private static final Pattern DIMENSION_PATTERN = Pattern.compile("^\\s*(\\d+)\\s*[xX×]\\s*(\\d+)\\s*$");

    @Id
    @Column(name = "model_id")
    private Long modelId;

    @Column(name = "product_id", unique = true)
    private Long productId;

    @Column(name = "model_name", nullable = false, length = 255)
    private String modelName;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spec")
    private Map<String, Object> spec = new LinkedHashMap<>();

    @Column(name = "stock_quantity", nullable = false)
    private Long stockQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LedModelStatus status = LedModelStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static LedModelEntity create(Long modelId, String modelName, Map<String, Object> spec, Long stockQuantity) {
        return createInternal(modelId, null, modelName, null, null, spec, stockQuantity, null);
    }

    public static LedModelEntity createForProduct(
            Long productId, String modelName, Map<String, Object> spec, Long stockQuantity) {
        return createInternal(productId, productId, modelName, null, null, spec, stockQuantity, null);
    }

    public static LedModelEntity createCatalogModel(
            Long modelId,
            Long productId,
            String modelName,
            Integer widthPx,
            Integer heightPx,
            Map<String, Object> spec,
            Long stockQuantity,
            LedModelStatus status) {
        return createInternal(modelId, productId, modelName, widthPx, heightPx, spec, stockQuantity, status);
    }

    private static LedModelEntity createInternal(
            Long modelId,
            Long productId,
            String modelName,
            Integer explicitWidthPx,
            Integer explicitHeightPx,
            Map<String, Object> spec,
            Long stockQuantity,
            LedModelStatus explicitStatus) {
        long resolvedStock = stockQuantity != null ? stockQuantity : 0L;
        Map<String, Object> resolvedSpec = spec != null ? new LinkedHashMap<>(spec) : new LinkedHashMap<>();
        int[] extractedDimensions = extractDimensions(resolvedSpec);

        LedModelEntity entity = new LedModelEntity();
        entity.setModelId(modelId);
        entity.setProductId(productId);
        entity.setModelName(modelName);
        entity.setWidthPx(resolveDimension(explicitWidthPx, extractedDimensions[0]));
        entity.setHeightPx(resolveDimension(explicitHeightPx, extractedDimensions[1]));
        entity.setSpec(resolvedSpec);
        entity.setStockQuantity(resolvedStock);
        entity.setStatus(explicitStatus != null
                ? explicitStatus
                : (resolvedStock > 0L ? LedModelStatus.ACTIVE : LedModelStatus.OUT_OF_STOCK));
        return entity;
    }

    public boolean hasSufficientStock(long requestedQuantity) {
        return requestedQuantity > 0 && stockQuantity != null && stockQuantity >= requestedQuantity;
    }

    public boolean isLowStock() {
        return stockQuantity != null && stockQuantity < LOW_STOCK_THRESHOLD;
    }

    /**
     * Extracts positive pixel dimensions from canonical ({@code widthPx}/{@code heightPx})
     * or legacy ({@code resolution}/{@code dimensions}) spec entries.
     */
    public static int[] extractDimensions(Map<String, Object> spec) {
        if (spec == null || spec.isEmpty()) {
            return new int[] {0, 0};
        }
        int width = parsePositiveInt(spec.get("widthPx"));
        int height = parsePositiveInt(spec.get("heightPx"));
        if (width > 0 && height > 0) {
            return new int[] {width, height};
        }

        for (String key : new String[] {"resolution", "dimensions"}) {
            Object raw = spec.get(key);
            if (raw instanceof String text) {
                Matcher matcher = DIMENSION_PATTERN.matcher(text);
                if (matcher.matches()) {
                    if (width <= 0) {
                        width = parsePositiveInt(matcher.group(1));
                    }
                    if (height <= 0) {
                        height = parsePositiveInt(matcher.group(2));
                    }
                }
            }
        }
        return new int[] {width, height};
    }

    private static Integer resolveDimension(Integer explicit, int extracted) {
        if (explicit != null && explicit > 0) {
            return explicit;
        }
        return extracted > 0 ? extracted : null;
    }

    private static int parsePositiveInt(Object raw) {
        if (raw instanceof Number number) {
            int val = number.intValue();
            return val > 0 ? val : 0;
        }
        if (raw instanceof String text) {
            try {
                int val = Integer.parseInt(text.strip());
                return val > 0 ? val : 0;
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
