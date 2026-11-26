package com.lumintra.backend.iam.api.request;

import static org.assertj.core.api.Assertions.*;

import com.lumintra.backend.iam.domain.model.InternalUserRole;

import jakarta.validation.Validation;

import org.junit.jupiter.api.*;

class CreateInternalUserRequestTest {
    @Test
    @DisplayName(
            "B: Normalize request and enforce required email, role and schema length boundaries")
    void validationBoundaries() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var v = factory.getValidator();
            var request =
                    new CreateInternalUserRequest(
                            " User@Example.com ", InternalUserRole.DESIGNER, " Name ");
            assertThat(request.email()).isEqualTo("user@example.com");
            assertThat(request.name()).isEqualTo("Name");
            assertThat(v.validate(request)).isEmpty();
            assertThat(
                            v.validate(
                                    new CreateInternalUserRequest(
                                            "a@b.co", InternalUserRole.STAFF, "a".repeat(255))))
                    .isEmpty();
            assertThat(new CreateInternalUserRequest("a@b.co", InternalUserRole.STAFF, " ").name())
                    .isNull();
            for (String email :
                    new String[] {null, "", " ", "not-email", "a".repeat(256) + "@b.co"})
                assertThat(
                                v.validate(
                                        new CreateInternalUserRequest(
                                                email, InternalUserRole.STAFF, null)))
                        .isNotEmpty();
            assertThat(v.validate(new CreateInternalUserRequest("a@b.co", null, null)))
                    .isNotEmpty();
            assertThat(
                            v.validate(
                                    new CreateInternalUserRequest(
                                            "a@b.co", InternalUserRole.STAFF, "a".repeat(256))))
                    .isNotEmpty();
        }
    }
}
