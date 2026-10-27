package com.lumintra.backend.iam.domain.service;

public interface GoogleIdentityVerifier {
    GoogleIdentity verify(String idToken);
    record GoogleIdentity(String subject, String email, String name, boolean authoritativeEmail) {}
}
