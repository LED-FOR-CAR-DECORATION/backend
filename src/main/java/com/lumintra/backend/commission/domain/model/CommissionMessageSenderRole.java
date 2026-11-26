package com.lumintra.backend.commission.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Sender roles supported in commission chat room")
public enum CommissionMessageSenderRole {
    SYSTEM,
    CUSTOMER,
    STAFF,
    DESIGNER
}
