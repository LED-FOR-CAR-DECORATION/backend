package com.lumintra.backend.iam.api.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.iam.api.request.CreateInternalUserRequest;
import com.lumintra.backend.iam.api.response.CreateInternalUserResponse;
import com.lumintra.backend.iam.application.CreateInternalUserUseCase;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.iam.EmailAlreadyRegisteredException;
import com.lumintra.backend.shared.security.*;

import io.micrometer.tracing.Tracer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CreateInternalUserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CreateInternalUserControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CreateInternalUserUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;
    static final String PATH = "/api/management/admin/users";
    static final String BODY =
            "{\"email\":\"staff@example.com\",\"role\":\"STAFF\",\"name\":\"Staff\"}";

    @Test
    @DisplayName(
            "N: Admin creation returns safe 201; HTTP validation, security, conflicts, failures and"
                    + " Swagger match the contract")
    void httpAndSwagger() throws Exception {
        when(useCase.execute(eq("1"), any()))
                .thenReturn(
                        new CreateInternalUserResponse(
                                42L, "staff@example.com", "Staff", "STAFF", AccountStatus.ACTIVE));
        mvc.perform(
                        post(PATH)
                                .with(user("1").roles("ADMIN"))
                                .contentType("application/json")
                                .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("STAFF"))
                .andExpect(jsonPath("$.data.accountId").value(42))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        clearInvocations(useCase);
        mvc.perform(post(PATH).contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        for (String role : new String[] {"MANAGER", "STAFF", "DESIGNER", "CUSTOMER"})
            mvc.perform(
                            post(PATH)
                                    .with(user("1").roles(role))
                                    .contentType("application/json")
                                    .content(BODY))
                    .andExpect(status().isForbidden());
        for (String body :
                new String[] {
                    "{}",
                    "{",
                    "{\"email\":\"bad\",\"role\":\"STAFF\"}",
                    BODY.replace("STAFF", "ADMIN"),
                    BODY.replace("STAFF", "CUSTOMER"),
                    BODY.replace("STAFF", "unknown"),
                    BODY.replace("STAFF", "staff")
                })
            mvc.perform(
                            post(PATH)
                                    .with(user("1").roles("ADMIN"))
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(useCase);
        doThrow(
                        new org.springframework.security.access.AccessDeniedException(
                                "Current admin role is required."))
                .when(useCase)
                .execute(anyString(), any());
        mvc.perform(
                        post(PATH)
                                .with(user("1").roles("ADMIN"))
                                .contentType("application/json")
                                .content(BODY))
                .andExpect(status().isForbidden());
        doThrow(new EmailAlreadyRegisteredException("Email is already registered"))
                .when(useCase)
                .execute(anyString(), any());
        mvc.perform(
                        post(PATH)
                                .with(user("1").roles("ADMIN"))
                                .contentType("application/json")
                                .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        doThrow(new IllegalStateException("private SQL password"))
                .when(useCase)
                .execute(anyString(), any());
        mvc.perform(
                        post(PATH)
                                .with(user("1").roles("ADMIN"))
                                .contentType("application/json")
                                .content(BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "private SQL password"))));
        var method =
                CreateInternalUserController.class.getMethod(
                        "create", Authentication.class, CreateInternalUserRequest.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(ApiResponses.class).value())
                .extracting(a -> a.responseCode())
                .containsExactlyInAnyOrder("201", "400", "401", "403", "409", "500");
        assertThat(CreateInternalUserResponse.class.getRecordComponents())
                .extracting(c -> c.getName())
                .containsExactly("accountId", "email", "name", "role", "status");
        for (Class<?> dto :
                new Class<?>[] {
                    CreateInternalUserRequest.class, CreateInternalUserResponse.class
                }) {
            assertThat(dto.getAnnotation(Schema.class)).isNotNull();
            for (var c : dto.getRecordComponents())
                assertThat(dto.getDeclaredField(c.getName()).getAnnotation(Schema.class))
                        .isNotNull();
        }
    }
}
