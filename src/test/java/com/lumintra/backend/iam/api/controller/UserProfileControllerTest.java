package com.lumintra.backend.iam.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.iam.api.response.CurrentProfileResponse;
import com.lumintra.backend.iam.application.GetCurrentProfileUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import com.lumintra.backend.shared.security.*;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

@WebMvcTest(UserProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class UserProfileControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean GetCurrentProfileUseCase useCase;
    @MockitoBean JwtTokenProvider tokenProvider;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    @DisplayName("N: Authenticated customer receives only public profile fields")
    void success() throws Exception {
        when(useCase.execute("42"))
                .thenReturn(
                        new CurrentProfileResponse(
                                42L,
                                "An",
                                "user@example.com",
                                "0901234567",
                                "avatar.png",
                                List.of("CUSTOMER")));
        mvc.perform(get("/api/users/me").with(user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("An"))
                .andExpect(jsonPath("$.data.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.failedAttempts").doesNotExist());
    }

    @Test
    @DisplayName("A: Anonymous request is denied before the use case")
    void anonymous() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("A: Invalid bearer token is denied")
    void invalidToken() throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("N: Employee can view their own profile")
    void employee() throws Exception {
        when(useCase.execute("7"))
                .thenReturn(
                        new CurrentProfileResponse(
                                7L, "Staff", "staff@example.com", null, null, List.of("ADMIN")));
        mvc.perform(get("/api/users/me").with(user("7").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"));
    }

    @Test
    @DisplayName("A: Query parameter cannot select another account")
    void ownership() throws Exception {
        when(useCase.execute("42"))
                .thenReturn(
                        new CurrentProfileResponse(
                                42L, null, "a@b.co", null, null, List.of("CUSTOMER")));
        mvc.perform(get("/api/users/me?accountId=99").with(user("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(42));
        verify(useCase).execute("42");
        verify(useCase, never()).execute("99");
    }

    @Test
    @DisplayName("A: Missing profile returns the standard 404 envelope")
    void missing() throws Exception {
        when(useCase.execute("42")).thenThrow(new UserNotFoundException("Account not found."));
        mvc.perform(get("/api/users/me").with(user("42")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("A: Inactive account returns 403")
    void inactive() throws Exception {
        when(useCase.execute("42"))
                .thenThrow(
                        new org.springframework.security.access.AccessDeniedException("Inactive"));
        mvc.perform(get("/api/users/me").with(user("42"))).andExpect(status().isForbidden());
    }
}
