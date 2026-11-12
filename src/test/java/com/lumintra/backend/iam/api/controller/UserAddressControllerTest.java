package com.lumintra.backend.iam.api.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lumintra.backend.iam.api.response.UserAddressResponse;
import com.lumintra.backend.iam.application.ListUserAddressesUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
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

    @Autowired private MockMvc mvc;
    @MockitoBean private ListUserAddressesUseCase useCase;
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
}
