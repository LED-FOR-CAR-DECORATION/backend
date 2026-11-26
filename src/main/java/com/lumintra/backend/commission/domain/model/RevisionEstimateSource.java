package com.lumintra.backend.commission.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Who set a revision delivery estimate")
public enum RevisionEstimateSource {
    /** Entered by the assigned designer (CMS-24). */
    DESIGNER,
    /** Applied by the system after the estimate window lapsed (BR-FL3-13, CMS-28). */
    SYSTEM_DEFAULT
}
