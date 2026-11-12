package com.lumintra.backend.iam.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lumintra.backend.iam.api.request.AddUserAddressRequest;
import com.lumintra.backend.iam.api.request.UpdateUserAddressRequest;
import com.lumintra.backend.iam.api.response.UserAddressResponse;
import com.lumintra.backend.iam.application.AddUserAddressUseCase;
import com.lumintra.backend.iam.application.ListUserAddressesUseCase;
import com.lumintra.backend.iam.application.UpdateUserAddressUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.commerce.AddressNotFoundException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@WebMvcTest(UserAddressController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class UserAddressControllerTest {

    private static final String PATH = "/api/users/me/addresses";
    private static final String VALID_ADD_BODY =
            """
            {
              "recipientName": "  Nguyen Van A  ",
              "recipientPhone": "  0901234567  ",
              "fullAddress": "  123 Nguyen Hue, Ben Nghe, District 1, Ho Chi Minh City  ",
              "latitude": 10.7768891234,
              "longitude": 106.7008065678,
              "isDefault": true
            }
            """;

    @Autowired private MockMvc mvc;
    private static final String VALID_UPDATE_BODY =
            """
            {
              "recipientName": "  Nguyen Van B  ",
              "recipientPhone": "  0912345678  ",
              "fullAddress": "  456 Le Loi, Ben Thanh, District 1, Ho Chi Minh City  ",
              "latitude": 10.773123456,
              "longitude": 106.698765432
            }
            """;

    @MockitoBean private ListUserAddressesUseCase useCase;
    @MockitoBean private AddUserAddressUseCase addUserAddressUseCase;
    @MockitoBean private UpdateUserAddressUseCase updateUserAddressUseCase;
    @MockitoBean private JwtTokenProvider tokenProvider;
    @MockitoBean private TokenBlacklistService tokenBlacklistService;
    @MockitoBean private Tracer tracer;

    @Test
    @DisplayName("IAM-22 UTCID01 [N]: AC01-AC04 CUSTOMER receives 200 OK with address list, coordinates and isDefault")
    void listAddresses_asCustomer_returns200WithSafeAddressList() throws Exception {
        Instant timestamp = Instant.parse("2026-11-10T02:15:00Z");
        UserAddressResponse defaultAddress =
                new UserAddressResponse(
                        10L,
                        "Nguyen Van A",
                        "0901234567",
                        "123 Nguyen Hue, Ben Nghe, District 1, HCMC",
                        new BigDecimal("10.7768890"),
                        new BigDecimal("106.7008060"),
                        true,
                        timestamp,
                        timestamp);
        when(useCase.execute("42")).thenReturn(List.of(defaultAddress));

        mvc.perform(get(PATH).with(user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].addressId").value(10))
                .andExpect(jsonPath("$.data[0].recipientName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data[0].recipientPhone").value("0901234567"))
                .andExpect(
                        jsonPath("$.data[0].fullAddress")
                                .value("123 Nguyen Hue, Ben Nghe, District 1, HCMC"))
                .andExpect(jsonPath("$.data[0].latitude").value(10.7768890))
                .andExpect(jsonPath("$.data[0].longitude").value(106.7008060))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-11-10T02:15:00Z"))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-11-10T02:15:00Z"))
                .andExpect(jsonPath("$.data[0].customerId").doesNotExist())
                .andExpect(jsonPath("$.data[0].default").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(useCase).execute("42");
    }

    @Test
    @DisplayName("IAM-22 UTCID02 [B]: AC04 CUSTOMER with no saved addresses receives 200 OK with empty array")
    void listAddresses_asCustomerNoAddresses_returns200EmptyArray() throws Exception {
        when(useCase.execute("42")).thenReturn(List.of());

        mvc.perform(get(PATH).with(user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(useCase).execute("42");
    }

    @Test
    @DisplayName("IAM-22 UTCID03 [A]: AC01 anonymous or invalid bearer token returns 401 UNAUTHORIZED")
    void listAddresses_unauthenticatedOrInvalidToken_returns401() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(get(PATH).header("Authorization", "Bearer invalid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("IAM-22 UTCID04 [A]: AC01 invalid subject in JWT propagates as 401 INVALID_CREDENTIALS")
    void listAddresses_invalidSubject_returns401InvalidCredentials() throws Exception {
        when(useCase.execute("invalid-subject"))
                .thenThrow(new InvalidCredentialsException("Invalid authenticated account."));

        mvc.perform(get(PATH).with(user("invalid-subject").roles("CUSTOMER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("IAM-22 UTCID05 [A]: AC01 EMPLOYEE roles (ADMIN, MANAGER, STAFF, DESIGNER) are rejected with 403 FORBIDDEN")
    void listAddresses_employeeRoles_returns403() throws Exception {
        for (String role : new String[] {"ADMIN", "MANAGER", "STAFF", "DESIGNER"}) {
            mvc.perform(get(PATH).with(user("7").roles(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("IAM-22 UTCID06 [A]: AC01 & AC02 ignores client customerId query param and maps missing/inactive DB account to 404/403")
    void listAddresses_antiIdorAndDbAccountCheck_maps404And403() throws Exception {
        when(useCase.execute("42")).thenReturn(List.of());
        mvc.perform(get(PATH + "?customerId=99").with(user("42").roles("CUSTOMER")))
                .andExpect(status().isOk());
        verify(useCase).execute("42");
        verify(useCase, never()).execute("99");

        when(useCase.execute("404")).thenThrow(new UserNotFoundException("Account not found."));
        mvc.perform(get(PATH).with(user("404").roles("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        when(useCase.execute("403")).thenThrow(new AccessDeniedException("Account is not active."));
        mvc.perform(get(PATH).with(user("403").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("IAM-22 UTCID07 [A]: AC05 unexpected database failure returns safe 500 without leaking internal SQL details")
    void listAddresses_databaseFailure_returns500() throws Exception {
        when(useCase.execute("42"))
                .thenThrow(new DataAccessResourceFailureException("sensitive SQL error details"));

        mvc.perform(get(PATH).with(user("42").roles("CUSTOMER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID01 [N]: AC01-AC05 CUSTOMER adds address and receives 201 Created with rounded coordinates and isDefault")
    void addAddress_asCustomer_returns201CreatedWithSafePayload() throws Exception {
        Instant timestamp = Instant.parse("2026-11-12T02:00:00Z");
        UserAddressResponse created =
                new UserAddressResponse(
                        10L,
                        "Nguyen Van A",
                        "0901234567",
                        "123 Nguyen Hue, Ben Nghe, District 1, Ho Chi Minh City",
                        new BigDecimal("10.7768891"),
                        new BigDecimal("106.7008066"),
                        true,
                        timestamp,
                        timestamp);
        when(addUserAddressUseCase.execute(eq("42"), any(AddUserAddressRequest.class)))
                .thenReturn(created);

        mvc.perform(
                        post(PATH)
                                .with(user("42").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_ADD_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.addressId").value(10))
                .andExpect(jsonPath("$.data.recipientName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.recipientPhone").value("0901234567"))
                .andExpect(
                        jsonPath("$.data.fullAddress")
                                .value("123 Nguyen Hue, Ben Nghe, District 1, Ho Chi Minh City"))
                .andExpect(jsonPath("$.data.latitude").value(10.7768891))
                .andExpect(jsonPath("$.data.longitude").value(106.7008066))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-12T02:00:00Z"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-11-12T02:00:00Z"))
                .andExpect(jsonPath("$.data.customerId").doesNotExist())
                .andExpect(jsonPath("$.data.default").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(addUserAddressUseCase)
                .execute(
                        "42",
                        new AddUserAddressRequest(
                                "Nguyen Van A",
                                "0901234567",
                                "123 Nguyen Hue, Ben Nghe, District 1, Ho Chi Minh City",
                                new BigDecimal("10.7768891234"),
                                new BigDecimal("106.7008065678"),
                                true));
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID02 [B]: AC03 & AC04 omitting isDefault defaults request flag to false and still returns 201 Created")
    void addAddress_omittingIsDefault_defaultsFalseAndReturns201() throws Exception {
        Instant timestamp = Instant.parse("2026-11-12T02:00:00Z");
        when(addUserAddressUseCase.execute(eq("42"), any(AddUserAddressRequest.class)))
                .thenReturn(
                        new UserAddressResponse(
                                10L,
                                "Nguyen Van A",
                                "0901234567",
                                "123 Nguyen Hue, District 1, HCMC",
                                new BigDecimal("10.7768891"),
                                new BigDecimal("106.7008066"),
                                true,
                                timestamp,
                                timestamp));

        mvc.perform(
                        post(PATH)
                                .with(user("42").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "recipientName": "Nguyen Van A",
                                          "recipientPhone": "0901234567",
                                          "fullAddress": "123 Nguyen Hue, District 1, HCMC",
                                          "latitude": 10.7768891,
                                          "longitude": 106.7008066
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        verify(addUserAddressUseCase)
                .execute(
                        "42",
                        new AddUserAddressRequest(
                                "Nguyen Van A",
                                "0901234567",
                                "123 Nguyen Hue, District 1, HCMC",
                                new BigDecimal("10.7768891"),
                                new BigDecimal("106.7008066"),
                                false));
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID03 [A]: AC03 invalid fields, >255 fullAddress, out-of-range coords or malformed JSON return 400 VALIDATION_FAILED")
    void addAddress_invalidPayloadOrMalformedJson_returns400() throws Exception {
        String tooLongAddress =
                "{\"recipientName\":\"An\",\"recipientPhone\":\"0901234567\",\"fullAddress\":\""
                        + "A".repeat(256)
                        + "\",\"latitude\":10.77,\"longitude\":106.70}";
        String outOfRangeLat =
                """
                {
                  "recipientName": "An",
                  "recipientPhone": "0901234567",
                  "fullAddress": "123 Nguyen Hue",
                  "latitude": 90.5,
                  "longitude": 106.70
                }
                """;
        String badPhone =
                """
                {
                  "recipientName": "An",
                  "recipientPhone": "abc",
                  "fullAddress": "123 Nguyen Hue",
                  "latitude": 10.77,
                  "longitude": 106.70
                }
                """;

        for (String payload : new String[] {tooLongAddress, outOfRangeLat, badPhone, "{"}) {
            mvc.perform(
                            post(PATH)
                                    .with(user("42").roles("CUSTOMER"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        verifyNoInteractions(addUserAddressUseCase);
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID04 [A]: AC01 anonymous/invalid Bearer returns 401 UNAUTHORIZED and invalid subject maps to 401 INVALID_CREDENTIALS")
    void addAddress_unauthenticatedOrInvalidSubject_returns401() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_ADD_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(
                        post(PATH)
                                .header("Authorization", "Bearer invalid-jwt")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_ADD_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(addUserAddressUseCase);

        when(addUserAddressUseCase.execute(eq("invalid-subject"), any()))
                .thenThrow(new InvalidCredentialsException("Invalid authenticated account."));

        mvc.perform(
                        post(PATH)
                                .with(user("invalid-subject").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_ADD_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID05 [A]: AC01 EMPLOYEE roles (ADMIN, MANAGER, STAFF, DESIGNER) are rejected with 403 FORBIDDEN")
    void addAddress_employeeRoles_returns403() throws Exception {
        for (String role : new String[] {"ADMIN", "MANAGER", "STAFF", "DESIGNER"}) {
            mvc.perform(
                            post(PATH)
                                    .with(user("7").roles(role))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(VALID_ADD_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        verifyNoInteractions(addUserAddressUseCase);
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID06 [A]: AC01 & AC02 ignores client-supplied customerId and maps DB missing/inactive account to 404/403")
    void addAddress_antiIdorAndDbAccountCheck_maps404And403() throws Exception {
        Instant timestamp = Instant.parse("2026-11-12T02:00:00Z");
        when(addUserAddressUseCase.execute(eq("42"), any()))
                .thenReturn(
                        new UserAddressResponse(
                                10L,
                                "An",
                                "0901234567",
                                "123 Nguyen Hue",
                                new BigDecimal("10.7768891"),
                                new BigDecimal("106.7008066"),
                                false,
                                timestamp,
                                timestamp));

        mvc.perform(
                        post(PATH + "?customerId=99")
                                .with(user("42").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "customerId": 99,
                                          "recipientName": "An",
                                          "recipientPhone": "0901234567",
                                          "fullAddress": "123 Nguyen Hue",
                                          "latitude": 10.7768891,
                                          "longitude": 106.7008066,
                                          "isDefault": false
                                        }
                                        """))
                .andExpect(status().isCreated());
        verify(addUserAddressUseCase).execute(eq("42"), any(AddUserAddressRequest.class));
        verify(addUserAddressUseCase, never()).execute(eq("99"), any());

        when(addUserAddressUseCase.execute(eq("404"), any()))
                .thenThrow(new UserNotFoundException("Account not found."));
        mvc.perform(
                        post(PATH)
                                .with(user("404").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_ADD_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        when(addUserAddressUseCase.execute(eq("403"), any()))
                .thenThrow(new AccessDeniedException("Account is not active."));
        mvc.perform(
                        post(PATH)
                                .with(user("403").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_ADD_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID07 [A]: AC05 unexpected database failure returns safe 500 without leaking internal SQL details")
    void addAddress_databaseFailure_returns500() throws Exception {
        when(addUserAddressUseCase.execute(anyString(), any()))
                .thenThrow(new DataAccessResourceFailureException("sensitive SQL error details"));

        mvc.perform(
                        post(PATH)
                                .with(user("42").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_ADD_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID01 [N]: AC01-AC05 CUSTOMER updates owned address and receives 200 OK with preserved isDefault and normalized payload")
    void updateAddress_asCustomer_returns200WithSafePayload() throws Exception {
        Instant createdAt = Instant.parse("2026-11-12T02:00:00Z");
        Instant updatedAt = Instant.parse("2026-11-12T03:00:00Z");
        UserAddressResponse updated =
                new UserAddressResponse(
                        10L,
                        "Nguyen Van B",
                        "0912345678",
                        "456 Le Loi, Ben Thanh, District 1, Ho Chi Minh City",
                        new BigDecimal("10.7731235"),
                        new BigDecimal("106.6987654"),
                        true,
                        createdAt,
                        updatedAt);
        when(updateUserAddressUseCase.execute(
                        eq("42"), eq(10L), any(UpdateUserAddressRequest.class)))
                .thenReturn(updated);

        mvc.perform(
                        put(PATH + "/10")
                                .with(user("42").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressId").value(10))
                .andExpect(jsonPath("$.data.recipientName").value("Nguyen Van B"))
                .andExpect(jsonPath("$.data.recipientPhone").value("0912345678"))
                .andExpect(
                        jsonPath("$.data.fullAddress")
                                .value("456 Le Loi, Ben Thanh, District 1, Ho Chi Minh City"))
                .andExpect(jsonPath("$.data.latitude").value(10.7731235))
                .andExpect(jsonPath("$.data.longitude").value(106.6987654))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-12T02:00:00Z"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-11-12T03:00:00Z"))
                .andExpect(jsonPath("$.data.customerId").doesNotExist())
                .andExpect(jsonPath("$.data.default").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(updateUserAddressUseCase)
                .execute(
                        "42",
                        10L,
                        new UpdateUserAddressRequest(
                                "Nguyen Van B",
                                "0912345678",
                                "456 Le Loi, Ben Thanh, District 1, Ho Chi Minh City",
                                new BigDecimal("10.773123456"),
                                new BigDecimal("106.698765432")));
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID02 [A]: AC02 & AC04 ignores client customerId, addressId, and isDefault tampering on PUT")
    void updateAddress_ignoresExtraCustomerIdAddressIdAndIsDefault() throws Exception {
        Instant timestamp = Instant.parse("2026-11-12T03:00:00Z");
        when(updateUserAddressUseCase.execute(
                        eq("42"), eq(10L), any(UpdateUserAddressRequest.class)))
                .thenReturn(
                        new UserAddressResponse(
                                10L,
                                "Nguyen Van B",
                                "0912345678",
                                "456 Le Loi, District 1, HCMC",
                                new BigDecimal("10.7731235"),
                                new BigDecimal("106.6987654"),
                                true,
                                timestamp,
                                timestamp));

        mvc.perform(
                        put(PATH + "/10?customerId=99")
                                .with(user("42").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "addressId": 999,
                                          "customerId": 99,
                                          "isDefault": false,
                                          "recipientName": "Nguyen Van B",
                                          "recipientPhone": "0912345678",
                                          "fullAddress": "456 Le Loi, District 1, HCMC",
                                          "latitude": 10.7731235,
                                          "longitude": 106.6987654
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressId").value(10))
                .andExpect(jsonPath("$.data.isDefault").value(true));

        verify(updateUserAddressUseCase)
                .execute(
                        "42",
                        10L,
                        new UpdateUserAddressRequest(
                                "Nguyen Van B",
                                "0912345678",
                                "456 Le Loi, District 1, HCMC",
                                new BigDecimal("10.7731235"),
                                new BigDecimal("106.6987654")));
        verify(updateUserAddressUseCase, never()).execute(eq("99"), any(), any());
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID03 [B]: AC02 non-numeric or non-positive path id returns 400 VALIDATION_FAILED")
    void updateAddress_invalidPathId_returns400() throws Exception {
        for (String badPath : new String[] {PATH + "/abc", PATH + "/0", PATH + "/-1"}) {
            mvc.perform(
                            put(badPath)
                                    .with(user("42").roles("CUSTOMER"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(VALID_UPDATE_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").value("id"));
        }

        verifyNoInteractions(updateUserAddressUseCase);
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID04 [A]: AC03 invalid body fields, >255 fullAddress, out-of-range coords or malformed JSON return 400 VALIDATION_FAILED")
    void updateAddress_invalidPayloadOrMalformedJson_returns400() throws Exception {
        String tooLongAddress =
                "{\"recipientName\":\"Binh\",\"recipientPhone\":\"0912345678\",\"fullAddress\":\""
                        + "A".repeat(256)
                        + "\",\"latitude\":10.77,\"longitude\":106.70}";
        String outOfRangeLng =
                """
                {
                  "recipientName": "Binh",
                  "recipientPhone": "0912345678",
                  "fullAddress": "456 Le Loi",
                  "latitude": 10.77,
                  "longitude": 180.5
                }
                """;
        String badPhone =
                """
                {
                  "recipientName": "Binh",
                  "recipientPhone": "abc",
                  "fullAddress": "456 Le Loi",
                  "latitude": 10.77,
                  "longitude": 106.70
                }
                """;

        for (String payload : new String[] {tooLongAddress, outOfRangeLng, badPhone, "{"}) {
            mvc.perform(
                            put(PATH + "/10")
                                    .with(user("42").roles("CUSTOMER"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        verifyNoInteractions(updateUserAddressUseCase);
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID05 [A]: AC01 anonymous/invalid Bearer returns 401 UNAUTHORIZED and invalid subject maps to 401 INVALID_CREDENTIALS")
    void updateAddress_unauthenticatedOrInvalidSubject_returns401() throws Exception {
        mvc.perform(
                        put(PATH + "/10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_UPDATE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(
                        put(PATH + "/10")
                                .header("Authorization", "Bearer invalid-jwt")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_UPDATE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(updateUserAddressUseCase);

        when(updateUserAddressUseCase.execute(eq("invalid-subject"), eq(10L), any()))
                .thenThrow(new InvalidCredentialsException("Invalid authenticated account."));

        mvc.perform(
                        put(PATH + "/10")
                                .with(user("invalid-subject").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_UPDATE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID06 [A]: AC01 EMPLOYEE roles (ADMIN, MANAGER, STAFF, DESIGNER) are rejected with 403 FORBIDDEN")
    void updateAddress_employeeRoles_returns403() throws Exception {
        for (String role : new String[] {"ADMIN", "MANAGER", "STAFF", "DESIGNER"}) {
            mvc.perform(
                            put(PATH + "/10")
                                    .with(user("7").roles(role))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(VALID_UPDATE_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        verifyNoInteractions(updateUserAddressUseCase);
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID07 [A]: AC01 & AC02 maps missing/foreign address to 404 ADDRESS_NOT_FOUND, missing account to 404 USER_NOT_FOUND, and inactive account to 403")
    void updateAddress_missingAddressOrAccountStatusErrors_map404And403() throws Exception {
        when(updateUserAddressUseCase.execute(eq("42"), eq(99L), any()))
                .thenThrow(new AddressNotFoundException("Delivery address not found."));
        mvc.perform(
                        put(PATH + "/99")
                                .with(user("42").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_UPDATE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Delivery address not found."));

        when(updateUserAddressUseCase.execute(eq("404"), eq(10L), any()))
                .thenThrow(new UserNotFoundException("Account not found."));
        mvc.perform(
                        put(PATH + "/10")
                                .with(user("404").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_UPDATE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        when(updateUserAddressUseCase.execute(eq("403"), eq(10L), any()))
                .thenThrow(new AccessDeniedException("Account is not active."));
        mvc.perform(
                        put(PATH + "/10")
                                .with(user("403").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_UPDATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID08 [A]: AC05 unexpected database failure returns safe 500 without leaking internal SQL details")
    void updateAddress_databaseFailure_returns500() throws Exception {
        when(updateUserAddressUseCase.execute(anyString(), eq(10L), any()))
                .thenThrow(new DataAccessResourceFailureException("sensitive SQL error details"));

        mvc.perform(
                        put(PATH + "/10")
                                .with(user("42").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_UPDATE_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
