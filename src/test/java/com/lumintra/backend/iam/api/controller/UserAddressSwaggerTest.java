package com.lumintra.backend.iam.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumintra.backend.iam.api.response.UserAddressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class UserAddressSwaggerTest {

    @Test
    @DisplayName("IAM-22 UTCID01 [N]: AC01 & AC04 Controller documents success and applicable error codes")
    void contract_documentsExpectedResponses() throws Exception {
        var method = UserAddressController.class.getMethod("listAddresses", Authentication.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(ApiResponses.class)).isNotNull();
        assertThat(method.getAnnotation(ApiResponses.class).value())
                .extracting(v -> v.responseCode())
                .containsExactlyInAnyOrder("200", "401", "403", "404", "500");
    }

    @Test
    @DisplayName("IAM-22 UTCID02 [N]: AC04 UserAddressResponse defines explicit safe allowlist without customerId")
    void dto_safeAllowlistAnnotatedWithSchema() {
        assertThat(UserAddressResponse.class.getRecordComponents())
                .extracting(c -> c.getName())
                .containsExactly(
                        "addressId",
                        "recipientName",
                        "recipientPhone",
                        "fullAddress",
                        "latitude",
                        "longitude",
                        "isDefault",
                        "createdAt",
                        "updatedAt");

        for (var field : UserAddressResponse.class.getDeclaredFields()) {
            assertThat(field.getAnnotation(Schema.class)).isNotNull();
        }
    }
}
