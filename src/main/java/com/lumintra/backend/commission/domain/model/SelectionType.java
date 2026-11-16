package com.lumintra.backend.commission.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Selection cardinality and input primitive type for a commission pricing rule")
public enum SelectionType {
    SINGLE_SELECT,
    MULTI_SELECT,
    QUANTITY,
    TEXT_INPUT
}
