package com.lumintra.backend.store.api.request;

import com.lumintra.backend.store.domain.model.ProductType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilterRequest {

    private ProductType productType;

    private String categoryTag;

    @Min(value = 0, message = "minPrice must be greater than or equal to 0")
    private BigDecimal minPrice;

    @Min(value = 0, message = "maxPrice must be greater than or equal to 0")
    private BigDecimal maxPrice;

    @Min(value = 0, message = "page must be greater than or equal to 0")
    @Builder.Default
    private Integer page = 0;

    @Min(value = 1, message = "size must be at least 1")
    @Max(value = 100, message = "size must not exceed 100")
    @Builder.Default
    private Integer size = 20;
}
