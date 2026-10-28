package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.EmailVerificationRequest;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import com.lumintra.backend.iam.domain.service.EmailVerificationStore;
import com.lumintra.backend.notification.domain.service.EmailProvider;
import com.lumintra.backend.shared.exception.iam.EmailVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class RegistrationVerificationService {
    private final EmailVerificationStore store;
    private final EmailProvider email;
    private final RegisterAccountService accounts;
    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();
    public RegistrationVerificationService(EmailVerificationStore store, EmailProvider email, RegisterAccountService accounts,
                                           @Value("${app.jwt.secret}") String secret) {
        this.store = store; this.email = email; this.accounts = accounts; this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }
    public void send(String address) {
        accounts.requirePending(address);
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String digest = digest(address, code);
        store.issue(address, digest);
        try { email.sendRegistrationOtp(address, code, "registration-" + UUID.randomUUID()); }
        catch (RuntimeException ex) {
            try { store.invalidate(address, digest); } catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }
    public RegisterResponse verify(EmailVerificationRequest request) {
        accounts.requirePending(request.email());
        if (!store.consume(request.email(), digest(request.email(), request.otp()))) {
            throw new EmailVerificationException("OTP_INVALID", "The verification code is invalid or expired.");
        }
        return accounts.activate(request.email());
    }
    private String digest(String address, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("registration:" + address + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("HMAC unavailable", ex); }
    }
}
