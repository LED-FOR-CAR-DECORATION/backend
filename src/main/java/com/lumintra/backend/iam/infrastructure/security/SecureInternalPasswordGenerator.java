package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.service.InternalPasswordGenerator;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureInternalPasswordGenerator implements InternalPasswordGenerator {
    private static final String[] GROUPS = {
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz", "0123456789", "!@#$%&*_-+"
    };
    private static final String ALPHABET = String.join("", GROUPS);
    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        char[] password = new char[20];
        for (int i = 0; i < GROUPS.length; i++)
            password[i] = GROUPS[i].charAt(random.nextInt(GROUPS[i].length()));
        for (int i = GROUPS.length; i < password.length; i++)
            password[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        for (int i = password.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char c = password[i];
            password[i] = password[j];
            password[j] = c;
        }
        return new String(password);
    }
}
