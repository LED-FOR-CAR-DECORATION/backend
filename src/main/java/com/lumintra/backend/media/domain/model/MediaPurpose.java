package com.lumintra.backend.media.domain.model;

/**
 * Strict enumeration of allowed upload purposes within Lumintra.
 * Replaces free-text client folder input with strict server-side scoping and authorization.
 */
public enum MediaPurpose {
    /**
     * Allowed for CUSTOMER (reference images when creating a commission). No targetId required.
     */
    COMMISSION_REFERENCE,

    /**
     * Allowed for owning CUSTOMER, assigned STAFF, or MANAGER/ADMIN. Requires active commissionId (targetId).
     */
    COMMISSION_CHAT,

    /**
     * Allowed only for DESIGNER. Requires commissionId (targetId) and assigned_designer_id ownership.
     */
    COMMISSION_DRAFT,

    /**
     * Allowed only for CUSTOMER. Requires deviceId (targetId) bound to the caller account.
     */
    WARRANTY_EVIDENCE,

    /**
     * Allowed only for DESIGNER, MANAGER, ADMIN (designer workspace or store character preview).
     */
    CHARACTER_ASSET,

    /**
     * Allowed only for MANAGER, ADMIN (legacy/generic store product media).
     */
    STORE_PRODUCT,

    /**
     * Allowed only for MANAGER, ADMIN (LED hardware model & product images under store/products/led/).
     */
    PRODUCT_LED,

    /**
     * Allowed only for MANAGER, ADMIN (bundle combo images under store/products/bundle/).
     */
    PRODUCT_BUNDLE,

    /**
     * Allowed only for MANAGER, ADMIN (subscription plan images under store/products/subscription/).
     */
    PRODUCT_SUBSCRIPTION;

    /**
     * Private purposes are stored in the private bucket and are only ever served through short-lived
     * signed GET URLs issued by an endpoint that has already authorized the caller.
     * Public purposes stay in the public bucket and are served through the CDN.
     */
    public boolean isPrivate() {
        return switch (this) {
            case COMMISSION_REFERENCE, COMMISSION_CHAT, COMMISSION_DRAFT, WARRANTY_EVIDENCE -> true;
            case CHARACTER_ASSET, STORE_PRODUCT, PRODUCT_LED, PRODUCT_BUNDLE, PRODUCT_SUBSCRIPTION -> false;
        };
    }
}
