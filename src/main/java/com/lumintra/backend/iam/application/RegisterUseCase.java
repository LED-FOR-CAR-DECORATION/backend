package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.RegisterRequest;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class RegisterUseCase {
    private final RegisterAccountService accounts;
    private final RegistrationVerificationService verification;
    public RegisterResponse execute(RegisterRequest request) {
        var response = accounts.create(request);
        // The database transaction has committed before calling Redis or the email provider.
        verification.send(response.email());
        return response;
    }
}
