package com.lumintra.backend.iam.api.request.validation;

import com.lumintra.backend.iam.api.request.ResetPasswordRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ResetPasswordMatchValidator implements ConstraintValidator<ResetPasswordMatch, ResetPasswordRequest> {

    @Override
    public boolean isValid(ResetPasswordRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        if (request.confirmPassword() == null || request.confirmPassword().isBlank()) {
            return true; // Optional confirmPassword allows clients submitting only (email, otp, newPassword)
        }
        if (request.newPassword() == null) {
            return true;
        }
        boolean match = request.newPassword().equals(request.confirmPassword());
        if (!match) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Password confirmation does not match")
                    .addPropertyNode("confirmPassword")
                    .addConstraintViolation();
        }
        return match;
    }
}
