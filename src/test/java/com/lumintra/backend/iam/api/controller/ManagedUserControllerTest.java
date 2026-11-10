package com.lumintra.backend.iam.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.iam.api.request.ManagedUserFilterRequest;
import com.lumintra.backend.iam.api.response.ManagedUserListResponse;
import com.lumintra.backend.iam.application.ListManagedUsersUseCase;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

@WebMvcTest(ManagedUserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ManagedUserControllerTest {
    static final String PATH = "/api/management/admin/users";
    @Autowired MockMvc mvc;
    @MockitoBean ListManagedUsersUseCase useCase;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    ManagedUserListResponse response() {
        var item =
                new ManagedUserListResponse.UserItem(
                        7L,
                        "Staff",
                        "staff@example.com",
                        null,
                        null,
                        List.of("STAFF"),
                        AccountStatus.ACTIVE,
                        true);
        var page = new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1);
        return new ManagedUserListResponse(page.getContent(), PaginationMeta.from(page));
    }

    @Test
    @DisplayName("N: Admin receives safe data envelope and pagination metadata")
    void admin() throws Exception {
        when(useCase.execute(eq("42"), any())).thenReturn(response());
        mvc.perform(get(PATH).with(user("42").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].accountId").value(7))
                .andExpect(jsonPath("$.data.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].failedAttempts").doesNotExist());
        verify(useCase).execute("42", new ManagedUserFilterRequest(null, null, null, null, null));
    }

    @Test
    @DisplayName("N: Manager may list users and normalized filters reach the use case")
    void manager() throws Exception {
        when(useCase.execute(eq("7"), any())).thenReturn(response());
        mvc.perform(
                        get(PATH)
                                .with(user("7").roles("MANAGER"))
                                .param("page", "1")
                                .param("size", "20")
                                .param("role", " staff ")
                                .param("active", "false")
                                .param("search", " Nguyen "))
                .andExpect(status().isOk());
        verify(useCase).execute("7", new ManagedUserFilterRequest(1, 20, "STAFF", false, "Nguyen"));
    }

    @Test
    @DisplayName("A: Anonymous requests return 401 before list execution")
    void anonymous() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("A: Invalid bearer token cannot list users")
    void invalidToken() throws Exception {
        mvc.perform(get(PATH).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("A: CUSTOMER and STAFF roles are rejected by method security")
    void forbiddenRoles() throws Exception {
        for (String role : new String[] {"CUSTOMER", "STAFF", "TECHNICIAN"}) {
            mvc.perform(get(PATH).with(user("42").roles(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("A: Negative page, excessive size and excessive offset return 400")
    void paginationValidation() throws Exception {
        for (String[] pair :
                new String[][] {
                    {"page", "-1"}, {"size", "0"}, {"size", "51"}, {"page", "1000001"}
                }) {
            mvc.perform(get(PATH).with(user("42").roles("ADMIN")).param(pair[0], pair[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("A: Non-numeric pagination and invalid active flags are rejected")
    void malformedFilters() throws Exception {
        for (String[] pair :
                new String[][] {{"page", "abc"}, {"size", "abc"}, {"active", "invalid"}}) {
            mvc.perform(get(PATH).with(user("42").roles("ADMIN")).param(pair[0], pair[1]))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("A: Oversized search input returns 400")
    void searchLength() throws Exception {
        mvc.perform(get(PATH).with(user("42").roles("ADMIN")).param("search", "A".repeat(256)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("A: Database role demotion is returned as 403 despite JWT ADMIN authority")
    void staleAuthority() throws Exception {
        when(useCase.execute(eq("42"), any()))
                .thenThrow(
                        new org.springframework.security.access.AccessDeniedException("Demoted"));
        mvc.perform(get(PATH).with(user("42").roles("ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A: Database failure returns safe 500 without SQL details")
    void outage() throws Exception {
        when(useCase.execute(eq("42"), any()))
                .thenThrow(
                        new org.springframework.dao.DataAccessResourceFailureException(
                                "private SQL details"));
        mvc.perform(get(PATH).with(user("42").roles("ADMIN")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
