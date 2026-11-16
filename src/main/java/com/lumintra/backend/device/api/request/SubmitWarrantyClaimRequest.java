package com.lumintra.backend.device.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitWarrantyClaimRequest(
        @NotBlank(message = "Issue description must not be blank.")
        @Size(min = 10, max = 1000, message = "Issue description must be between 10 and 1000 characters.")
        String issueDescription,

        @Size(max = 5, message = "Evidence media URLs must not exceed 5 items.")
        List<@Size(max = 500, message = "Evidence media URL must not exceed 500 characters.") String> evidenceMediaUrls
) {}
