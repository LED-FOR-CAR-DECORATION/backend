package com.lumintra.backend.store.infrastructure.caching;

public final class StoreCacheNames {

    private StoreCacheNames() {
        // Prevent instantiation
    }

    /**
     * Cache namespace for store product list queries with filter parameters.
     * Full Redis Key format: store:products:{queryKey}
     * TTL: 15 minutes
     */
    public static final String PRODUCTS = "store:products";

    /**
     * Cache namespace for single product details.
     * Full Redis Key format: store:product:{id}
     * TTL: 30 minutes
     */
    public static final String PRODUCT = "store:product";

    /**
     * Cache namespace for store category tags.
     * Full Redis Key format: store:tags:{key}
     * TTL: 60 minutes
     */
    public static final String TAGS = "store:tags";
}
