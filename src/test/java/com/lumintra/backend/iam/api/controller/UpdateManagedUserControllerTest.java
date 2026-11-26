package com.lumintra.backend.iam.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.iam.api.request.UpdateManagedUserRequest;
import com.lumintra.backend.iam.application.UpdateManagedUserUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UpdateManagedUserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class UpdateManagedUserControllerTest {
    private static final String PATH = "/api/management/admin/users/{id}";
    private static final String BODY = "{\"role\":\"MANAGER\"}";

    @Autowired MockMvc mvc;
    @MockitoBean UpdateManagedUserUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    @DisplayName("N: Administrator update returns 204 with no response body")
    void updates() throws Exception {
        mvc.perform(
                        put(PATH, 2)
                                .with(user("1").roles("ADMIN"))
                                .contentType("application/json")
                                .content(BODY))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(useCase)
                .execute("1", 2L, new UpdateManagedUserRequest(null, null, null, "MANAGER", null));
    }

    @Test
    @DisplayName("B: Reject zero IDs and empty or invalid updates")
    void validates() throws Exception {
        mvc.perform(
                        put(PATH, 0)
                                .with(user("1").roles("ADMIN"))
                                .contentType("application/json")
                                .content(BODY))
                .andExpect(status().isBadRequest());
        for (String body : new String[] {"{}", "{\"role\":\"\"}"}) {
            mvc.perform(
                            put(PATH, 2)
                                    .with(user("1").roles("ADMIN"))
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("A: Reject anonymous requests and non-administrator callers")
    void restrictsAccess() throws Exception {
        mvc.perform(put(PATH, 2).contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        put(PATH, 2)
                                .with(user("1").roles("MANAGER"))
                                .contentType("application/json")
                                .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }
}
