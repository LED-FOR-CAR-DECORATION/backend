package com.lumintra.backend.store.api.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.lumintra.backend.store.domain.model.ProductStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateStoreProductStatusRequest(@NotNull Status status) {
    public enum Status {
        UNPUBLISHED,
        PUBLISHED,
        ARCHIVED;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Status fromJson(String value) {
            return Status.valueOf(value);
        }

        public ProductStatus toProductStatus() {
            return ProductStatus.valueOf(name());
        }
    }
}
