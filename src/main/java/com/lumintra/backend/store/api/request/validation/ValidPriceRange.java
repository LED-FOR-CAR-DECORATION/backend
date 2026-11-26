package com.lumintra.backend.store.api.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidPriceRangeValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPriceRange {
    String message() default "Minimum price cannot be greater than maximum price";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
