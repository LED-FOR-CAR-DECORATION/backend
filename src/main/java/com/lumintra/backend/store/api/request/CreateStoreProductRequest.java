package com.lumintra.backend.store.api.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lumintra.backend.store.domain.model.ProductType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(description = "Create an unpublished LED or CHARACTER product")
public record CreateStoreProductRequest(
        @NotBlank @Size(max = 255) String productName,
        @NotNull @Schema(allowableValues = {"LED", "CHARACTER"}) ProductType productType,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
        String description,
        @Size(max = 500) @Pattern(regexp = "https?://\\S+") @URL String thumbnailUrl,
        List<@NotBlank @Size(max = 500) @Pattern(regexp = "https?://\\S+") @URL String> galleryUrls,
        List<@NotNull @Positive Long> tagIds,
        @Positive Long characterVersionId,
        @Valid LedDetails ledDetails) {

    public CreateStoreProductRequest {
        productName = productName == null ? null : productName.strip();
    }

    @JsonIgnore
    @AssertTrue(
            message =
                    "CHARACTER requires characterVersionId and excludes ledDetails. LED excludes"
                        + " characterVersionId.")
    public boolean isLinkValid() {
        return productType == null
                || (productType == ProductType.CHARACTER
                        && characterVersionId != null
                        && ledDetails == null)
                || (productType == ProductType.LED && characterVersionId == null);
    }

    @Schema(description = "Optional LED model to create with the product")
    public record LedDetails(
            @NotBlank @Size(max = 255) String modelName,
            Map<String, Object> spec,
            @NotNull @PositiveOrZero Long stockQuantity) {
        public LedDetails {
            modelName = modelName == null ? null : modelName.strip();
        }
    }
}
