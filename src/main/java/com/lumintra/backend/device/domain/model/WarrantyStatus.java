package com.lumintra.backend.device.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Hardware warranty coverage state derived from claim activation and 180-day expiry")
public enum WarrantyStatus {
    NOT_ACTIVATED,
    ACTIVE,
    EXPIRED
}
