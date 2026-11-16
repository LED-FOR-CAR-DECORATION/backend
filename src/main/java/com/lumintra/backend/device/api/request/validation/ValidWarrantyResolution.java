package com.lumintra.backend.device.api.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidWarrantyResolutionValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidWarrantyResolution {
    String message() default "resolutionNotes is required when rejecting or completing a warranty claim";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
