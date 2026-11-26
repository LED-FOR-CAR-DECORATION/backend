package com.lumintra.backend.commerce.api.request.validation;

import com.lumintra.backend.commerce.api.request.CheckoutRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidCheckoutDeliveryValidator implements ConstraintValidator<ValidCheckoutDelivery, CheckoutRequest> {

    @Override
    public boolean isValid(CheckoutRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        if (request.deliveryAddressId() != null && request.shippingDetails() != null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("deliveryAddressId")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }
}
