package com.lumintra.backend.store.api.response;

import com.lumintra.backend.store.domain.model.ProductType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummaryResponse {

    private Long productId;
    private String productName;
    private String description;
    private ProductType productType;
    private BigDecimal basePrice;
    private String thumbnailUrl;
    private List<String> galleryUrls;
    private List<TagResponse> tags;
    private Instant createdAt;
}
