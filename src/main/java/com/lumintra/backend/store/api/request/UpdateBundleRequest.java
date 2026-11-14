package com.lumintra.backend.store.api.request;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.util.List;

public record UpdateBundleRequest(
        @NotBlank @Size(max = 255) String bundleName,
        String description,
        @Size(max = 500) @Pattern(regexp = "https?://\\S+") @URL String thumbnailUrl,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal bundlePrice,
        @Size(min = 1) List<@NotNull @Valid Item> items) {
    public UpdateBundleRequest {
        bundleName = bundleName == null ? null : bundleName.strip();
    }

    public record Item(
            @Positive Long modelId, @Positive Long versionId, @NotNull @Positive Integer quantity) {
        @JsonIgnore
        @AssertTrue(message = "Provide exactly one of modelId or versionId")
        public boolean isReferenceValid() {
            return (modelId == null) != (versionId == null);
        }
    }
}
