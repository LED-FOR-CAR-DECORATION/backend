package com.lumintra.backend.commerce.domain.service;

public interface CustomerSubscriptionBonusLookupService {

    /**
     * Returns the highest per-commission free revision allowance across all active,
     * non-expired subscriptions owned by the given customer (0 if none).
     */
    int findMaxFreeCommissionRevisions(Long customerId);
}
