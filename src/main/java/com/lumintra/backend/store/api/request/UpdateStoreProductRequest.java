package com.lumintra.backend.store.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.util.List;

@Schema(
        description =
                "Replace product details. Omit tagIds to keep existing tags, or send an empty array"
                    + " to clear them.")
public record UpdateStoreProductRequest(
        @NotBlank @Size(max = 255) String productName,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
        String description,
        @Size(max = 500) @Pattern(regexp = "https?://\\S+") @URL String thumbnailUrl,
        List<@NotBlank @Size(max = 500) @Pattern(regexp = "https?://\\S+") @URL String> galleryUrls,
        List<@NotNull @Positive Long> tagIds) {
    public UpdateStoreProductRequest {
        productName = productName == null ? null : productName.strip();
    }
}
