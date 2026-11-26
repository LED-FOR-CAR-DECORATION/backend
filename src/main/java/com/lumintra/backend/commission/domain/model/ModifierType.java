package com.lumintra.backend.commission.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Deterministic price calculation modifier applied when an option is selected")
public enum ModifierType {
    FLAT_ADD,
    BASE_PERCENT,
    SUBTOTAL_PERCENT
}
