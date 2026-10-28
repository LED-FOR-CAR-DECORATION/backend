package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.*;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import com.lumintra.backend.iam.application.RegistrationVerificationService;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor @Tag(name = "Authentication")
public class RegistrationVerificationController {
    private final RegistrationVerificationService verification;
    @PostMapping("/verify-email")
    @Operation(summary = "Confirm registration email", description = "Consumes a six-digit Redis OTP and activates the pending account. Codes expire in five minutes and allow at most five incorrect attempts.")
    public ResponseEntity<ApiResponse<RegisterResponse>> verify(@Valid @RequestBody EmailVerificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(verification.verify(request)));
    }
    @PostMapping("/resend-verification")
    @Operation(summary = "Resend registration verification", description = "Sends a new code to a pending account. A 60-second cooldown applies. Returns no body.")
    public ResponseEntity<Void> resend(@Valid @RequestBody ResendVerificationRequest request) {
        verification.send(request.email());
        return ResponseEntity.accepted().build();
    }
}
