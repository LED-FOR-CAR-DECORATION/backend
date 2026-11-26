package com.lumintra.backend.store.api.request.validation;

import com.lumintra.backend.store.api.request.ProductFilterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPriceRangeValidator implements ConstraintValidator<ValidPriceRange, ProductFilterRequest> {

    @Override
    public boolean isValid(ProductFilterRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        if (request.minPrice() != null && request.maxPrice() != null) {
            if (request.minPrice().compareTo(request.maxPrice()) > 0) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Minimum price cannot be greater than maximum price")
                        .addPropertyNode("minPrice")
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
