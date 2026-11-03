package com.lumintra.backend.iam.api.request.validation;

import com.lumintra.backend.iam.api.request.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchValidator implements ConstraintValidator<PasswordMatch, RegisterRequest> {
    @Override
    public boolean isValid(RegisterRequest request, ConstraintValidatorContext context) {
        if (request.password() == null || request.confirmPassword() == null) {
            return true; // Let @NotBlank handle nulls
        }
        return request.password().equals(request.confirmPassword());
    }
}