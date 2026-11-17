package com.lumintra.backend.commission.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Message types supported in commission chat room")
public enum CommissionMessageType {
    SYSTEM,
    TEXT,
    IMAGE,
    DELIVERABLE_CARD,
    EXTRA_FEE_CARD
}
