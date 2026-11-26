package com.lumintra.backend.dashboard.infrastructure.persistence.repository;

import java.math.BigDecimal;

public record CategoryRevenueRow(
        String productType,
        BigDecimal revenue,
        long unitsSold
) {}
