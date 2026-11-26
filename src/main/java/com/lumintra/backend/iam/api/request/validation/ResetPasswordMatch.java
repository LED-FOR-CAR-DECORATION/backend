package com.lumintra.backend.iam.api.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ResetPasswordMatchValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ResetPasswordMatch {
    String message() default "Password confirmation does not match";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
