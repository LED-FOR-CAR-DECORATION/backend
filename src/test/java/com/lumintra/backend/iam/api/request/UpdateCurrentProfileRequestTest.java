package com.lumintra.backend.iam.api.request;

import static org.assertj.core.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UpdateCurrentProfileRequestTest {
    static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    static final Validator validator = factory.getValidator();

    @AfterAll
    static void close() {
        factory.close();
    }

    @Test
    @DisplayName("N: Trim supported fields and preserve Unicode names")
    void normalization() {
        var request =
                new UpdateCurrentProfileRequest(
                        "  Nguyễn Văn An  ", "  +84901234567 ", " https://example.com/a.png ");
        assertThat(request.name()).isEqualTo("Nguyễn Văn An");
        assertThat(request.phone()).isEqualTo("+84901234567");
        assertThat(request.avatar()).isEqualTo("https://example.com/a.png");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("B: Phone accepts 8 and 15 digits, with or without leading plus")
    void phoneBoundaries() {
        for (String phone :
                new String[] {
                    "12345678", "123456789012345", "+12345678", "+123456789012345", "0901234567"
                }) {
            assertThat(validator.validate(new UpdateCurrentProfileRequest("An", phone, null)))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("A: Reject short, long, embedded-space and alphabetic phone formats")
    void invalidPhones() {
        for (String phone :
                new String[] {
                    "1234567",
                    "1234567890123456",
                    "+123 45678",
                    "090abc4567",
                    "++84901234567",
                    "090-123-4567"
                }) {
            assertThat(validator.validate(new UpdateCurrentProfileRequest("An", phone, null)))
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("B: Null and blank optional fields normalize to null")
    void clearOptionals() {
        var request = new UpdateCurrentProfileRequest("An", "  ", "  ");
        assertThat(request.phone()).isNull();
        assertThat(request.avatar()).isNull();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("B: Name accepts 1 and 255 characters")
    void nameBoundaries() {
        for (String name : new String[] {"A", "A".repeat(255)})
            assertThat(validator.validate(new UpdateCurrentProfileRequest(name, null, null)))
                    .isEmpty();
    }

    @Test
    @DisplayName("A: Reject missing, blank and overly long names")
    void invalidNames() {
        for (String name : new String[] {null, "", "   ", "A".repeat(256)})
            assertThat(validator.validate(new UpdateCurrentProfileRequest(name, null, null)))
                    .isNotEmpty();
    }

    @Test
    @DisplayName("N: HTTP and HTTPS avatar URLs are accepted")
    void validAvatar() {
        for (String url :
                new String[] {"https://example.com/avatar.png", "http://example.com/avatar.png"})
            assertThat(validator.validate(new UpdateCurrentProfileRequest("An", null, url)))
                    .isEmpty();
    }

    @Test
    @DisplayName("A: Reject malformed, unsupported scheme and oversized avatar URLs")
    void invalidAvatar() {
        for (String url :
                new String[] {
                    "not-a-url",
                    "javascript:alert(1)",
                    "ftp://example.com/a",
                    "https://",
                    "https://example.com/a b",
                    "https://example.com/" + "a".repeat(255)
                }) {
            assertThat(validator.validate(new UpdateCurrentProfileRequest("An", null, url)))
                    .isNotEmpty();
        }
    }
}
