package com.lumintra.backend.commission.api.request;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;

@Schema(description = "Query parameters for filtering and paginating the designer commission queue")
public record DesignerCommissionQueueFilterRequest(
        @Schema(description = "Optional filter by exact commission lifecycle status across all 13 states", example = "IN_PROGRESS", nullable = true)
        CommissionStatus status,

        @Schema(description = "Sort direction on createdAt timestamp (ASC or DESC)", example = "DESC", defaultValue = "DESC")
        @Pattern(regexp = "^(?i)(ASC|DESC)$", message = "sortDirection must be ASC or DESC")
        String sortDirection,

        @Schema(description = "Zero-based page index (0 to 1000000)", example = "0", defaultValue = "0")
        @Min(value = 0, message = "page must be greater than or equal to 0")
        @Max(value = 1_000_000, message = "page must not exceed 1000000")
        Integer page,

        @Schema(description = "Page size (1 to 100)", example = "20", defaultValue = "20")
        @Min(value = 1, message = "size must be at least 1")
        @Max(value = 100, message = "size must not exceed 100")
        Integer size
) {
    public DesignerCommissionQueueFilterRequest {
        sortDirection = (sortDirection != null && !sortDirection.isBlank())
                ? sortDirection.trim().toUpperCase(Locale.ROOT)
                : "DESC";
        page = (page != null) ? page : 0;
        size = (size != null) ? size : 20;
    }

    public DesignerCommissionQueueFilterRequest() {
        this(null, "DESC", 0, 20);
    }

    public DesignerCommissionQueueFilterRequest(Integer page, Integer size) {
        this(null, "DESC", page, size);
    }

    public Pageable toPageable() {
        Sort.Direction direction = "ASC".equalsIgnoreCase(this.sortDirection)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, "createdAt").and(Sort.by(direction, "commissionId"));
        return PageRequest.of(this.page, this.size, sort);
    }
}
