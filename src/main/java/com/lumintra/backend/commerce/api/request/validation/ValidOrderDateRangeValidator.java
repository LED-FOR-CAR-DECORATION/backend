package com.lumintra.backend.commerce.api.request.validation;

import com.lumintra.backend.commerce.api.request.OrderFilterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidOrderDateRangeValidator implements ConstraintValidator<ValidOrderDateRange, OrderFilterRequest> {

    @Override
    public boolean isValid(OrderFilterRequest request, ConstraintValidatorContext context) {
        if (request == null || request.fromDate() == null || request.toDate() == null) {
            return true;
        }
        if (request.fromDate().isAfter(request.toDate())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("fromDate")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }
}
