package com.lumintra.backend.commerce.api.request.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class StaffTargetOrderStatusValidator implements ConstraintValidator<StaffTargetOrderStatus, String> {

    private static final Set<String> ALLOWED_VALUES = Set.of(
            "PROCESSING",
            "CANCELED",
            "CANCELLED"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return ALLOWED_VALUES.contains(value.trim());
    }
}
