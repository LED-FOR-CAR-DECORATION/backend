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
     * Allowed only for MANAGER, ADMIN (product thumbnails and store catalog galleries).
     */
    STORE_PRODUCT
}
