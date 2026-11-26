package com.lumintra.backend.dashboard.infrastructure.persistence.repository;

import java.math.BigDecimal;

public record DailyRevenueRow(
        int day,
        BigDecimal revenue,
        long orderCount
) {}
