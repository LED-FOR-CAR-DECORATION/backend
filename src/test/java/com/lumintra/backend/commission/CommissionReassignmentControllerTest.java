package com.lumintra.backend.commission;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lumintra.backend.commission.api.controller.CommissionReassignmentController;
import com.lumintra.backend.commission.api.request.ReassignCommissionRequest;
import com.lumintra.backend.commission.api.response.ReassignCommissionResponse;
import com.lumintra.backend.commission.application.ReassignCommissionUseCase;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionReassignmentException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import com.lumintra.backend.shared.exception.commission.InvalidDesignerAssignmentException;
import com.lumintra.backend.shared.exception.commission.InvalidStaffAssignmentException;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@WebMvcTest(CommissionReassignmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CommissionReassignmentControllerTest {
    private static final String PATH = "/api/management/commissions/105/reassign";
    private static final Set<String> OVERRIDE_ROLES = Set.of("MANAGER", "ADMIN");
    private static final String BODY = "{\"designerId\":6,\"reason\":\"Designer on leave\"}";

    @Autowired MockMvc mvc;
    @MockitoBean ReassignCommissionUseCase useCase;
    @MockitoBean InternalEmployeeAccessService employees;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    @DisplayName("AC01 [N]: MANAGER and ADMIN can reassign")
    void managerAndAdminReassign() throws Exception {
        when(employees.requireActiveEmployeeId(any(), eq(OVERRIDE_ROLES))).thenReturn(2L);
        when(useCase.execute(eq(105L), any(), eq(2L)))
                .thenReturn(ReassignCommissionResponse.builder()
                        .commissionId(105L)
                        .status(CommissionStatus.IN_PROGRESS)
                        .assignedStaffId(3L)
                        .assignedDesignerId(6L)
                        .slaDueAt(Instant.parse("2026-11-23T10:00:00Z"))
                        .isSlaOverdue(false)
                        .updatedAt(Instant.parse("2026-11-20T10:00:00Z"))
                        .build());

        for (String role : List.of("MANAGER", "ADMIN")) {
            mvc.perform(patch(PATH).with(user("42").roles(role)).contentType("application/json").content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.assignedDesignerId").value(6))
                    .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.data.slaDueAt").value("2026-11-23T10:00:00Z"))
                    .andExpect(jsonPath("$.data.isSlaOverdue").value(false));
        }
        verify(useCase, org.mockito.Mockito.times(2)).execute(105L,
                new ReassignCommissionRequest(null, 6L, "Designer on leave"), 2L);
    }

    @Test
    @DisplayName("AC01 [A]: guests get 401; CUSTOMER, STAFF and DESIGNER get 403")
    void rejectsOtherRoles() throws Exception {
        mvc.perform(patch(PATH).contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        for (String role : Set.of("CUSTOMER", "STAFF", "DESIGNER")) {
            mvc.perform(patch(PATH).with(user("42").roles(role)).contentType("application/json").content(BODY))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("AC01 [A]: missing target, blank or too long reason, non-positive ids or id give 400")
    void rejectsInvalidBodies() throws Exception {
        for (String invalid : List.of(
                "{}",
                "{\"reason\":\"No target\"}",
                "{\"designerId\":6}",
                "{\"designerId\":6,\"reason\":\"   \"}",
                "{\"designerId\":6,\"reason\":\"" + "x".repeat(501) + "\"}",
                "{\"designerId\":0,\"reason\":\"Leave\"}",
                "{\"staffId\":-1,\"reason\":\"Leave\"}")) {
            mvc.perform(patch(PATH).with(user("42").roles("MANAGER")).contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch(PATH.replace("105", "0")).with(user("42").roles("MANAGER"))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("[B]: reason of exactly 500 characters is accepted")
    void acceptsMaxLengthReason() throws Exception {
        when(employees.requireActiveEmployeeId(any(), eq(OVERRIDE_ROLES))).thenReturn(2L);
        when(useCase.execute(eq(105L), any(), eq(2L)))
                .thenReturn(ReassignCommissionResponse.builder().commissionId(105L).build());

        mvc.perform(patch(PATH).with(user("42").roles("MANAGER")).contentType("application/json")
                        .content("{\"staffId\":4,\"reason\":\"" + "x".repeat(500) + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC01-AC04 [A]: use case errors map to 404, 400 and 409")
    void mapsUseCaseErrors() throws Exception {
        when(employees.requireActiveEmployeeId(any(), eq(OVERRIDE_ROLES))).thenReturn(2L);
        Map<RuntimeException, Integer> cases = Map.of(
                new CommissionNotFoundException("missing"), 404,
                new InvalidCommissionReassignmentException("already assigned"), 400,
                new InvalidStaffAssignmentException("not staff"), 400,
                new InvalidDesignerAssignmentException("not designer"), 400,
                new InvalidCommissionStatusTransitionException("completed"), 409);
        for (var entry : cases.entrySet()) {
            when(useCase.execute(eq(105L), any(), eq(2L))).thenThrow(entry.getKey());
            mvc.perform(patch(PATH).with(user("42").roles("MANAGER")).contentType("application/json").content(BODY))
                    .andExpect(status().is(entry.getValue()));
        }
    }
}
