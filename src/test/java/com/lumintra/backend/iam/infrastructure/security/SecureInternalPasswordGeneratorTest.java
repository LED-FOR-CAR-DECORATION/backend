package com.lumintra.backend.iam.infrastructure.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;

import java.util.HashSet;

class SecureInternalPasswordGeneratorTest {
    @Test
    @DisplayName(
            "N: Generate independent 20-character passwords containing every required character"
                    + " group")
    void securePasswords() {
        var generator = new SecureInternalPasswordGenerator();
        var samples = new HashSet<String>();
        for (int i = 0; i < 200; i++) {
            String password = generator.generate();
            assertThat(password)
                    .hasSize(20)
                    .matches(".*[A-Z].*")
                    .matches(".*[a-z].*")
                    .matches(".*[0-9].*")
                    .matches(".*[!@#$%&*_+\\-].*");
            samples.add(password);
        }
        assertThat(samples).hasSize(200);
    }
}
