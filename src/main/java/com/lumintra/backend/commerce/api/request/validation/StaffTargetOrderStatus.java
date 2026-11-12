package com.lumintra.backend.commerce.api.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = StaffTargetOrderStatusValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StaffTargetOrderStatus {

    String message() default "status must be one of PROCESSING, COMPLETED, CANCELED, or CANCELLED";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
