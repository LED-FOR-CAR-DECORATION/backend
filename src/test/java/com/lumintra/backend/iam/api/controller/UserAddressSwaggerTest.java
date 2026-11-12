package com.lumintra.backend.iam.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumintra.backend.iam.api.request.AddUserAddressRequest;
import com.lumintra.backend.iam.api.request.UpdateUserAddressRequest;
import com.lumintra.backend.iam.api.response.UserAddressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

    @Test
    @DisplayName(
            "IAM-23 UTCID01 [N]: AC01-AC05 POST addAddress documents Bearer auth and 201/400/401/403/404/500 codes")
    void addAddressContract_documentsExpectedResponses() throws Exception {
        var method =
                UserAddressController.class.getMethod(
                        "addAddress", Authentication.class, AddUserAddressRequest.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(SecurityRequirement.class).name()).isEqualTo("bearerAuth");
        assertThat(method.getAnnotation(ApiResponses.class)).isNotNull();
        assertThat(method.getAnnotation(ApiResponses.class).value())
                .extracting(v -> v.responseCode())
                .containsExactlyInAnyOrder("201", "400", "401", "403", "404", "500");
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID02 [N]: AC02 & AC03 AddUserAddressRequest defines safe allowlist without customerId")
    void addAddressRequest_safeAllowlistAnnotatedWithSchema() {
        assertThat(AddUserAddressRequest.class.getRecordComponents())
                .extracting(c -> c.getName())
                .containsExactly(
                        "recipientName",
                        "recipientPhone",
                        "fullAddress",
                        "latitude",
                        "longitude",
                        "isDefault");

        for (var field : AddUserAddressRequest.class.getDeclaredFields()) {
            assertThat(field.getAnnotation(Schema.class)).isNotNull();
        }
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID01 [N]: AC01-AC05 PUT updateAddress documents Bearer auth and 200/400/401/403/404/500 codes")
    void updateAddressContract_documentsExpectedResponses() throws Exception {
        var method =
                UserAddressController.class.getMethod(
                        "updateAddress",
                        Authentication.class,
                        Long.class,
                        UpdateUserAddressRequest.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(SecurityRequirement.class).name()).isEqualTo("bearerAuth");
        assertThat(method.getAnnotation(ApiResponses.class)).isNotNull();
        assertThat(method.getAnnotation(ApiResponses.class).value())
                .extracting(v -> v.responseCode())
                .containsExactlyInAnyOrder("200", "400", "401", "403", "404", "500");
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID02 [N]: AC02-AC04 UpdateUserAddressRequest defines safe allowlist without customerId, addressId, or isDefault")
    void updateAddressRequest_safeAllowlistAnnotatedWithSchema() {
        assertThat(UpdateUserAddressRequest.class.getRecordComponents())
                .extracting(c -> c.getName())
                .containsExactly(
                        "recipientName",
                        "recipientPhone",
                        "fullAddress",
                        "latitude",
                        "longitude");

        for (var field : UpdateUserAddressRequest.class.getDeclaredFields()) {
            assertThat(field.getAnnotation(Schema.class)).isNotNull();
        }
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID01 [N]: AC01-AC05 DELETE deleteAddress documents Bearer auth and 204/400/401/403/404/500 codes")
    void deleteAddressContract_documentsExpectedResponses() throws Exception {
        var method =
                UserAddressController.class.getMethod(
                        "deleteAddress", Authentication.class, Long.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(SecurityRequirement.class).name()).isEqualTo("bearerAuth");
        var responses = method.getAnnotation(ApiResponses.class);
        assertThat(responses).isNotNull();
        assertThat(responses.value())
                .extracting(v -> v.responseCode())
                .containsExactlyInAnyOrder("204", "400", "401", "403", "404", "500");
    }
}
