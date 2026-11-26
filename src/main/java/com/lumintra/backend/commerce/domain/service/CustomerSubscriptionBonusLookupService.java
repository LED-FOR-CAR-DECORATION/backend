package com.lumintra.backend.commerce.domain.service;

public interface CustomerSubscriptionBonusLookupService {

    /**
     * Returns the total remaining complimentary commission revision credits across all active,
     * non-expired subscriptions owned by the given customer (shared across all commissions).
     */
    int findExtraCommissionRevisions(Long customerId);

    /**
     * Atomically consumes 1 complimentary commission revision credit from the customer's oldest
     * active subscription pool. Must be invoked inside an active transaction.
     *
     * @return {@code true} if 1 credit was deducted, {@code false} if the customer has no remaining credits
     */
    boolean consumeOneExtraCommissionRevision(Long customerId);
}
