package com.lumintra.backend.commerce.api.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidCheckoutDeliveryValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCheckoutDelivery {

    String message() default "Provide either deliveryAddressId or shippingDetails, not both";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
