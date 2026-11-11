package com.lumintra.backend.iam.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.iam.api.response.ManagedUserDetailResponse;
import com.lumintra.backend.iam.application.GetManagedUserDetailUseCase;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

@WebMvcTest(ManagedUserDetailController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ManagedUserDetailControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean GetManagedUserDetailUseCase useCase;
    @MockitoBean JwtTokenProvider tokenProvider;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;
    static final String PATH = "/api/management/admin/users/";

    @Test
    @DisplayName(
            "N: Admin and manager receive safe profile, roles and lockout data for the requested"
                    + " account")
    void successfulDetail() throws Exception {
        var response =
                ManagedUserDetailResponse.builder()
                        .accountId(42L)
                        .name("Staff")
                        .email("staff@example.com")
                        .phone("0901234567")
                        .avatar("https://example.com/avatar.png")
                        .role("STAFF")
                        .status(AccountStatus.SUSPENDED)
                        .lockout(
                                new ManagedUserDetailResponse.LockoutState(
                                        true, Instant.parse("2026-11-10T12:15:00Z"), 5))
                        .build();
        when(useCase.execute("1", 42L)).thenReturn(response);
        for (String role : new String[] {"ADMIN", "MANAGER"}) {
            mvc.perform(get(PATH + "42").with(user("1").roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accountId").value(42))
                    .andExpect(jsonPath("$.data.role").value("STAFF"))
                    .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
                    .andExpect(jsonPath("$.data.lockout.locked").value(true))
                    .andExpect(jsonPath("$.data.lockout.failedAttempts").value(5))
                    .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
        }
        verify(useCase, times(2)).execute("1", 42L);
    }

    @Test
    @DisplayName(
            "A: Reject unauthenticated or unauthorized requests, invalid IDs, customer targets and"
                    + " safe infrastructure errors")
    void failureContracts() throws Exception {
        mvc.perform(get(PATH + "42")).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "42").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        for (String role : new String[] {"CUSTOMER", "STAFF", "TECHNICIAN"}) {
            mvc.perform(get(PATH + "42").with(user("1").roles(role)))
                    .andExpect(status().isForbidden());
        }
        for (String id : new String[] {"0", "-1", "abc", "9223372036854775808"}) {
            mvc.perform(get(PATH + id).with(user("1").roles("ADMIN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(useCase);
        doThrow(new AccessDeniedException("Customer target")).when(useCase).execute("1", 42L);
        mvc.perform(get(PATH + "42").with(user("1").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        doThrow(new UserNotFoundException("User not found.")).when(useCase).execute("1", 42L);
        mvc.perform(get(PATH + "42").with(user("1").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        doThrow(new IllegalStateException("private database details"))
                .when(useCase)
                .execute("1", 42L);
        mvc.perform(get(PATH + "42").with(user("1").roles("ADMIN")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
