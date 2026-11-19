package com.lumintra.backend.commission;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.commission.api.controller.CommissionRevisionCancellationController;
import com.lumintra.backend.commission.application.CancelRevisionCheckoutUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.commission.*;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommissionRevisionCancellationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CommissionRevisionCancellationControllerTest {
    static final String PATH = "/api/commissions/105/revision-card/cancel";
    @Autowired MockMvc mvc;
    @MockitoBean CancelRevisionCheckoutUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void returnsNoContentForOwningCustomer() throws Exception {
        mvc.perform(post(PATH).with(user("501").roles("CUSTOMER")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(useCase).execute(105L, 501L);
    }

    @Test
    void rejectsGuestsOtherRolesAndInvalidId() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        for (String role : new String[] {"STAFF", "MANAGER", "ADMIN", "DESIGNER"}) {
            mvc.perform(post(PATH).with(user("501").roles(role))).andExpect(status().isForbidden());
        }
        for (String id : new String[] {"0", "-1", "abc"}) {
            mvc.perform(post(PATH.replace("105", id)).with(user("501").roles("CUSTOMER")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void rejectsInvalidCustomerPrincipal() throws Exception {
        for (String principal : new String[] {"email@example.com", "0", "-1"}) {
            mvc.perform(post(PATH).with(user(principal).roles("CUSTOMER")))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void mapsDomainAndPaymentErrors() throws Exception {
        doThrow(new CommissionOwnershipMismatchException("Owner only"))
                .doThrow(new CommissionNotFoundException("Missing"))
                .doThrow(new InvalidCommissionStatusTransitionException("Wrong state"))
                .doThrow(
                        new com.lumintra.backend.shared.exception.payment
                                .PaymentSessionNotCancellableException("Already paid"))
                .when(useCase)
                .execute(105L, 501L);
        for (int statusCode : new int[] {403, 404, 409, 409}) {
            mvc.perform(post(PATH).with(user("501").roles("CUSTOMER")))
                    .andExpect(status().is(statusCode));
        }
    }
}
