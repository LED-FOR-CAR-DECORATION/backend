package com.lumintra.backend.commission.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "UI control presentation type used to render a commission pricing rule")
public enum UiType {
    RADIO,
    DROPDOWN,
    CHECKBOX,
    NUMBER,
    TEXT
}
