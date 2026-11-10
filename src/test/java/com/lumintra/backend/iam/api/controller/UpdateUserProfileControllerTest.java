package com.lumintra.backend.iam.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.iam.api.request.UpdateCurrentProfileRequest;
import com.lumintra.backend.iam.api.response.CurrentProfileResponse;
import com.lumintra.backend.iam.application.UpdateCurrentProfileUseCase;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

@WebMvcTest(UpdateUserProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class UpdateUserProfileControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UpdateCurrentProfileUseCase useCase;
    @MockitoBean JwtTokenProvider tokenProvider;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;
    static final String BODY =
            "{\"name\":\"  New Name "
                + " \",\"phone\":\"+84901234567\",\"avatar\":\"https://example.com/avatar.png\"}";

    @Test
    @DisplayName("N: Customer updates own profile with normalized request and safe response")
    void customer() throws Exception {
        when(useCase.execute(eq("42"), any()))
                .thenReturn(
                        new CurrentProfileResponse(
                                42L,
                                "New Name",
                                "user@example.com",
                                "+84901234567",
                                "https://example.com/avatar.png",
                                List.of("CUSTOMER")));
        mvc.perform(
                        put("/api/users/me")
                                .with(user("42").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        verify(useCase)
                .execute(
                        "42",
                        new UpdateCurrentProfileRequest(
                                "New Name", "+84901234567", "https://example.com/avatar.png"));
    }

    @Test
    @DisplayName("N: Employee can update own profile without changing role")
    void employee() throws Exception {
        when(useCase.execute(eq("7"), any()))
                .thenReturn(
                        new CurrentProfileResponse(
                                7L,
                                "New Name",
                                "staff@example.com",
                                null,
                                null,
                                List.of("MANAGER")));
        mvc.perform(
                        put("/api/users/me")
                                .with(user("7").roles("MANAGER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("MANAGER"));
    }

    @Test
    @DisplayName("A: Anonymous requests are rejected before the use case")
    void anonymous() throws Exception {
        mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("A: Invalid bearer token cannot update a profile")
    void invalidToken() throws Exception {
        mvc.perform(
                        put("/api/users/me")
                                .header("Authorization", "Bearer invalid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("A: Invalid phone returns field validation errors without writes")
    void invalidPhone() throws Exception {
        mvc.perform(
                        put("/api/users/me")
                                .with(user("42"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"An\",\"phone\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("phone"));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("A: Missing name and invalid avatar are rejected")
    void invalidFields() throws Exception {
        for (String body :
                new String[] {"{}", "{\"name\":\"An\",\"avatar\":\"javascript:alert(1)\"}"}) {
            mvc.perform(
                            put("/api/users/me")
                                    .with(user("42"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("A: Malformed JSON returns the standard validation envelope")
    void malformedBody() throws Exception {
        mvc.perform(
                        put("/api/users/me")
                                .with(user("42"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("A: Client-supplied account ID and sensitive fields cannot change update target")
    void ownership() throws Exception {
        when(useCase.execute(eq("42"), any()))
                .thenReturn(
                        new CurrentProfileResponse(
                                42L, "An", "user@example.com", null, null, List.of("CUSTOMER")));
        mvc.perform(
                        put("/api/users/me?accountId=99")
                                .with(user("42"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"An\",\"accountId\":99,\"email\":\"other@example.com\",\"roles\":[\"ADMIN\"],\"passwordHash\":\"injected\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(42))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("CUSTOMER"));
        verify(useCase).execute("42", new UpdateCurrentProfileRequest("An", null, null));
        verify(useCase, never()).execute(eq("99"), any());
    }

    @Test
    @DisplayName("A: Missing profile returns 404")
    void missingProfile() throws Exception {
        when(useCase.execute(anyString(), any()))
                .thenThrow(new UserNotFoundException("Customer profile not found."));
        mvc.perform(
                        put("/api/users/me")
                                .with(user("42"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("A: Inactive account returns 403")
    void inactive() throws Exception {
        when(useCase.execute(anyString(), any()))
                .thenThrow(
                        new org.springframework.security.access.AccessDeniedException("Inactive"));
        mvc.perform(
                        put("/api/users/me")
                                .with(user("42"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A: Persistence failure returns safe 500 instead of success")
    void storageFailure() throws Exception {
        when(useCase.execute(anyString(), any()))
                .thenThrow(
                        new org.springframework.dao.DataAccessResourceFailureException(
                                "private storage details"));
        mvc.perform(
                        put("/api/users/me")
                                .with(user("42"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
