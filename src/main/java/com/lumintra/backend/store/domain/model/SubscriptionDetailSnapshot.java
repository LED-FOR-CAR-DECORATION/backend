package com.lumintra.backend.store.domain.model;

public record SubscriptionDetailSnapshot(
        Long productId,
        String planCode,
        String planName,
        boolean isLifetime,
        Integer durationDays,
        boolean allAccessCharacters,
        int maxCommissionRevisions,
        int maxMobileProfiles
) {}
