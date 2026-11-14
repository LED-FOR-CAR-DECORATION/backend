package com.lumintra.backend.store.api.request;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.util.List;

public record CreateBundleRequest(
        @NotBlank @Size(max = 255) String bundleName,
        String description,
        @Size(max = 500) @Pattern(regexp = "https?://\\S+") @URL String thumbnailUrl,
        List<@NotBlank @Size(max = 500) @Pattern(regexp = "https?://\\S+") @URL String> galleryUrls,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal bundlePrice,
        List<@NotNull @Positive Long> tagIds,
        @NotEmpty List<@NotNull @Valid Item> items) {
    public CreateBundleRequest {
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
