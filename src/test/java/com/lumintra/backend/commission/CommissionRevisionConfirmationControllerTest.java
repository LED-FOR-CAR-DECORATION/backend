package com.lumintra.backend.commission;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.commission.api.controller.CommissionRevisionConfirmationController;
import com.lumintra.backend.commission.api.response.ConfirmRevisionCardResponse;
import com.lumintra.backend.commission.application.ConfirmRevisionCardUseCase;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
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

import java.math.BigDecimal;

@WebMvcTest(CommissionRevisionConfirmationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CommissionRevisionConfirmationControllerTest {
    static final String PATH = "/api/commissions/105/revision-card/confirm";
    @Autowired MockMvc mvc;
    @MockitoBean ConfirmRevisionCardUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void confirmsForCustomerWithResponseEnvelope() throws Exception {
        when(useCase.execute(105L, 501L, 402L))
                .thenReturn(
                        new ConfirmRevisionCardResponse(
                                105L,
                                402L,
                                CommissionStatus.CUSTOMER_REVISION_REQUESTED,
                                1,
                                BigDecimal.ZERO,
                                null));
        mvc.perform(
                        post(PATH)
                                .with(user("501").roles("CUSTOMER"))
                                .contentType("application/json")
                                .content("{\"messageId\":402}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CUSTOMER_REVISION_REQUESTED"));
        verify(useCase).execute(105L, 501L, 402L);
    }

    @Test
    void rejectsGuestsOtherRolesAndInvalidIdentifiers() throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content("{\"messageId\":402}"))
                .andExpect(status().isUnauthorized());
        for (String role : new String[] {"STAFF", "ADMIN", "MANAGER", "DESIGNER"}) {
            mvc.perform(
                            post(PATH)
                                    .with(user("501").roles(role))
                                    .contentType("application/json")
                                    .content("{\"messageId\":402}"))
                    .andExpect(status().isForbidden());
        }
        for (String body : new String[] {"{}", "{\"messageId\":0}", "{\"messageId\":-1}"}) {
            mvc.perform(
                            post(PATH)
                                    .with(user("501").roles("CUSTOMER"))
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(
                        post(PATH.replace("105", "0"))
                                .with(user("501").roles("CUSTOMER"))
                                .contentType("application/json")
                                .content("{\"messageId\":402}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(useCase);
    }

    @Test
    void returnsPaidSessionForQrCheckout() throws Exception {
        var session =
                com.lumintra.backend.payment.api.response.PaymentSessionResponse.builder()
                        .paymentCode("LUMREV105ABC")
                        .targetType(
                                com.lumintra.backend.payment.domain.model.PaymentTargetType
                                        .COMMISSION_REVISION)
                        .amount(new BigDecimal("35000.00"))
                        .build();
        when(useCase.execute(105L, 501L, 402L))
                .thenReturn(
                        new ConfirmRevisionCardResponse(
                                105L,
                                402L,
                                CommissionStatus.AWAITING_EXTRA_PAYMENT,
                                2,
                                new BigDecimal("35000.00"),
                                session));
        mvc.perform(
                        post(PATH)
                                .with(user("501").roles("CUSTOMER"))
                                .contentType("application/json")
                                .content("{\"messageId\":402}"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.paymentSession.targetType").value("COMMISSION_REVISION"))
                .andExpect(jsonPath("$.data.paymentSession.paymentCode").value("LUMREV105ABC"))
                .andExpect(jsonPath("$.data.paymentSession.amount").value(35000));
    }

    @Test
    void rejectsInvalidCustomerPrincipal() throws Exception {
        for (String principal : new String[] {"email@example.com", "0", "-1"}) {
            mvc.perform(
                            post(PATH)
                                    .with(user(principal).roles("CUSTOMER"))
                                    .contentType("application/json")
                                    .content("{\"messageId\":402}"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void mapsOwnershipMissingAndConflictErrors() throws Exception {
        when(useCase.execute(105L, 501L, 402L))
                .thenThrow(new CommissionOwnershipMismatchException("Owner only"))
                .thenThrow(new CommissionNotFoundException("Missing"))
                .thenThrow(new InvalidCommissionStatusTransitionException("Already confirmed"));
        for (int code : new int[] {403, 404, 409}) {
            mvc.perform(
                            post(PATH)
                                    .with(user("501").roles("CUSTOMER"))
                                    .contentType("application/json")
                                    .content("{\"messageId\":402}"))
                    .andExpect(status().is(code));
        }
    }
}
