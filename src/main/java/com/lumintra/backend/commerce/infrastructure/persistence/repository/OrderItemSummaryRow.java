package com.lumintra.backend.commerce.infrastructure.persistence.repository;

public record OrderItemSummaryRow(
        Long orderId,
        Long productId
) {}
