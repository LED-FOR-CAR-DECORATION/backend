package com.lumintra.backend.device.api.request.validation;

import com.lumintra.backend.device.api.request.ProcessWarrantyClaimRequest;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidWarrantyResolutionValidator implements ConstraintValidator<ValidWarrantyResolution, ProcessWarrantyClaimRequest> {

    @Override
    public boolean isValid(ProcessWarrantyClaimRequest request, ConstraintValidatorContext context) {
        if (request == null || request.status() == null) {
            return true;
        }

        if (request.status() == WarrantyTicketStatus.REJECTED || request.status() == WarrantyTicketStatus.COMPLETED) {
            if (request.resolutionNotes() == null || request.resolutionNotes().isBlank()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("resolutionNotes is required and must not be blank when status is " + request.status())
                        .addPropertyNode("resolutionNotes")
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
