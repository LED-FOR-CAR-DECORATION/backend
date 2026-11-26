package com.lumintra.backend.commission;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.commission.api.controller.CommissionRevisionCardController;
import com.lumintra.backend.commission.application.PostRevisionCardUseCase;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

@WebMvcTest(CommissionRevisionCardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CommissionRevisionCardControllerTest {
    private static final String PATH = "/api/management/commissions/105/revision-card";

    @Autowired MockMvc mvc;
    @MockitoBean PostRevisionCardUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @MockitoBean InternalEmployeeAccessService employees;

    @Test
    void resolvesEmployeeIdAndCreatesCardForStaff() throws Exception {
        when(employees.requireActiveEmployeeId("42", Set.of("STAFF"))).thenReturn(3L);
        when(useCase.execute(eq(105L), eq(3L), any()))
                .thenReturn(
                        com.lumintra.backend.commission.api.response.CommissionMessageResponse
                                .builder()
                                .messageId(12L)
                                .commissionId(105L)
                                .messageType(
                                        com.lumintra.backend.commission.domain.model
                                                .CommissionMessageType.EXTRA_FEE_CARD)
                                .content("Brighter outline")
                                .build());
        mvc.perform(
                        post(PATH)
                                .with(user("42").roles("STAFF"))
                                .contentType("application/json")
                                .content("{\"brief\":\" Brighter outline \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.messageType").value("EXTRA_FEE_CARD"));
        verify(useCase)
                .execute(
                        eq(105L),
                        eq(3L),
                        eq(
                                new com.lumintra.backend.commission.api.request
                                        .PostRevisionCardRequest("Brighter outline")));
    }

    @Test
    void rejectsGuestsOtherRolesAndInvalidBrief() throws Exception {
        var body = "{\"brief\":\"Make it blue\"}";
        mvc.perform(post(PATH).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        for (String role : Set.of("CUSTOMER", "DESIGNER", "MANAGER", "ADMIN")) {
            mvc.perform(
                            post(PATH)
                                    .with(user("42").roles(role))
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isForbidden());
        }
        for (String invalid :
                Set.of("{}", "{\"brief\":\" \"}", "{\"brief\":\"" + "x".repeat(2001) + "\"}")) {
            mvc.perform(
                            post(PATH)
                                    .with(user("42").roles("STAFF"))
                                    .contentType("application/json")
                                    .content(invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(
                        post(PATH.replace("105", "0"))
                                .with(user("42").roles("STAFF"))
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(useCase, employees);
    }

    @Test
    void translatesOwnershipMissingAndStateErrors() throws Exception {
        when(employees.requireActiveEmployeeId("42", Set.of("STAFF"))).thenReturn(3L);
        when(useCase.execute(eq(105L), eq(3L), any()))
                .thenThrow(
                        new com.lumintra.backend.shared.exception.commission
                                .CommissionStaffLockException("Assigned staff only"))
                .thenThrow(
                        new com.lumintra.backend.shared.exception.commission
                                .CommissionNotFoundException("Missing"))
                .thenThrow(
                        new com.lumintra.backend.shared.exception.commission
                                .InvalidCommissionStatusTransitionException("Wrong state"));
        for (int expected : new int[] {403, 404, 409}) {
            mvc.perform(
                            post(PATH)
                                    .with(user("42").roles("STAFF"))
                                    .contentType("application/json")
                                    .content("{\"brief\":\"Change color\"}"))
                    .andExpect(status().is(expected));
        }
    }
}
