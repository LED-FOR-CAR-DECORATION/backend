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

import com.lumintra.backend.commission.api.controller.CommissionRevisionEstimateController;
import com.lumintra.backend.commission.api.request.InputRevisionEstimateRequest;
import com.lumintra.backend.commission.api.response.InputRevisionEstimateResponse;
import com.lumintra.backend.commission.application.InputRevisionEstimateUseCase;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.RevisionEstimateSource;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@WebMvcTest(CommissionRevisionEstimateController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CommissionRevisionEstimateControllerTest {
    private static final String PATH = "/api/management/commissions/105/estimate";

    @Autowired MockMvc mvc;
    @MockitoBean InputRevisionEstimateUseCase useCase;
    @MockitoBean InternalEmployeeAccessService employees;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void assignedDesignerSubmitsEstimate() throws Exception {
        when(employees.requireActiveEmployeeId("42", Set.of("DESIGNER"))).thenReturn(5L);
        when(useCase.execute(eq(105L), eq(5L), any()))
                .thenReturn(InputRevisionEstimateResponse.builder()
                        .commissionId(105L)
                        .status(CommissionStatus.IN_PROGRESS)
                        .revisionRequestId(31L)
                        .revisionNumber(2)
                        .estimatedHours(36)
                        .estimateSource(RevisionEstimateSource.DESIGNER)
                        .slaDueAt(Instant.parse("2026-11-21T10:00:00Z"))
                        .customerDeliveryDeadline(Instant.parse("2026-11-22T10:00:00Z"))
                        .estimatedAt(Instant.parse("2026-11-19T22:00:00Z"))
                        .build());

        mvc.perform(patch(PATH)
                        .with(user("42").roles("DESIGNER"))
                        .contentType("application/json")
                        .content("{\"estimatedHours\":36}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.estimatedHours").value(36))
                .andExpect(jsonPath("$.data.customerDeliveryDeadline").value("2026-11-22T10:00:00Z"));
        verify(useCase).execute(105L, 5L, new InputRevisionEstimateRequest(36));
    }

    @Test
    void rejectsGuestsAndNonDesigners() throws Exception {
        var body = "{\"estimatedHours\":24}";
        mvc.perform(patch(PATH).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        for (String role : Set.of("CUSTOMER", "STAFF", "MANAGER", "ADMIN")) {
            mvc.perform(patch(PATH).with(user("42").roles(role)).contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void rejectsInvalidEstimateAndId() throws Exception {
        for (String invalid : List.of("{}", "{\"estimatedHours\":0}", "{\"estimatedHours\":73}",
                "{\"estimatedHours\":-5}", "{\"estimatedHours\":\"abc\"}")) {
            mvc.perform(patch(PATH).with(user("42").roles("DESIGNER")).contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch(PATH.replace("105", "0"))
                        .with(user("42").roles("DESIGNER"))
                        .contentType("application/json")
                        .content("{\"estimatedHours\":24}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(useCase);
    }

    @Test
    void mapsOwnershipAndStateErrors() throws Exception {
        when(employees.requireActiveEmployeeId("42", Set.of("DESIGNER"))).thenReturn(5L);
        when(useCase.execute(eq(105L), eq(5L), any()))
                .thenThrow(new CommissionOwnershipMismatchException("not assigned"))
                .thenThrow(new InvalidCommissionStatusTransitionException("wrong state"));
        var request = patch(PATH).with(user("42").roles("DESIGNER"))
                .contentType("application/json").content("{\"estimatedHours\":24}");
        mvc.perform(request).andExpect(status().isForbidden());
        mvc.perform(request).andExpect(status().isConflict());
    }
}
