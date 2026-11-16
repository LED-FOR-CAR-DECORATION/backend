package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.request.CustomerCommissionFilterRequest;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse.CustomerCommissionSummaryItem;
import com.lumintra.backend.commission.application.ListCustomerCommissionsUseCase;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommissionController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        GlobalExceptionHandler.class
})
@ActiveProfiles("test")
class CommissionControllerTest {

    private static final String MY_COMMISSIONS_PATH = "/api/commissions/my";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListCustomerCommissionsUseCase listCustomerCommissionsUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("CMS-04 Controller UTCID01 [N]: Authenticated CUSTOMER lists personal commissions with default pagination")
    void listMyCommissions_customerRole_returns200OkWithPaginatedItems() throws Exception {
        CustomerCommissionSummaryItem item = CustomerCommissionSummaryItem.builder()
                .commissionId(501L)
                .title("Cyberpunk Neko Pixel Art")
                .tierCode("ADVANCED")
                .targetMatrixDimensions("32x64")
                .status(CommissionStatus.PENDING_PAYMENT)
                .initialPrice(new BigDecimal("180000.00"))
                .extraRevisionFee(BigDecimal.ZERO)
                .totalPrice(new BigDecimal("180000.00"))
                .revisionsUsed(0)
                .maxRevisions(3)
                .activePaymentCode("LMT-CMS-501")
                .quoteExpiresAt(Instant.parse("2026-11-19T10:00:00Z"))
                .createdAt(Instant.parse("2026-11-16T08:00:00Z"))
                .updatedAt(Instant.parse("2026-11-16T09:00:00Z"))
                .build();

        PaginationMeta meta = PaginationMeta.builder()
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(listCustomerCommissionsUseCase.execute(eq(10L), any(CustomerCommissionFilterRequest.class)))
                .thenReturn(CustomerCommissionListResponse.builder().items(List.of(item)).meta(meta).build());

        mockMvc.perform(get(MY_COMMISSIONS_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("10").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].commissionId").value(501))
                .andExpect(jsonPath("$.data[0].title").value("Cyberpunk Neko Pixel Art"))
                .andExpect(jsonPath("$.data[0].tierCode").value("ADVANCED"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data[0].totalPrice").value(180000.00))
                .andExpect(jsonPath("$.data[0].activePaymentCode").value("LMT-CMS-501"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(1));

        ArgumentCaptor<CustomerCommissionFilterRequest> captor = ArgumentCaptor.forClass(CustomerCommissionFilterRequest.class);
        verify(listCustomerCommissionsUseCase).execute(eq(10L), captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(0);
        assertThat(captor.getValue().size()).isEqualTo(20);
        assertThat(captor.getValue().sortDirection()).isEqualTo("DESC");
    }

    @Test
    @DisplayName("CMS-04 Controller UTCID02 [N]: Supports status filter, ASC sortDirection, and custom page/size")
    void listMyCommissions_withStatusAndAscSort_passesFilterToUseCase() throws Exception {
        PaginationMeta meta = PaginationMeta.builder()
                .page(1)
                .size(10)
                .totalElements(0)
                .totalPages(0)
                .hasNext(false)
                .hasPrevious(true)
                .build();

        when(listCustomerCommissionsUseCase.execute(eq(10L), any(CustomerCommissionFilterRequest.class)))
                .thenReturn(CustomerCommissionListResponse.builder().items(List.of()).meta(meta).build());

        mockMvc.perform(get(MY_COMMISSIONS_PATH)
                        .param("status", "IN_PROGRESS")
                        .param("sortDirection", "asc")
                        .param("page", "1")
                        .param("size", "10")
                        .with(SecurityMockMvcRequestPostProcessors.user("10").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.size").value(10));

        ArgumentCaptor<CustomerCommissionFilterRequest> captor = ArgumentCaptor.forClass(CustomerCommissionFilterRequest.class);
        verify(listCustomerCommissionsUseCase).execute(eq(10L), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(CommissionStatus.IN_PROGRESS);
        assertThat(captor.getValue().sortDirection()).isEqualTo("ASC");
        assertThat(captor.getValue().page()).isEqualTo(1);
        assertThat(captor.getValue().size()).isEqualTo(10);
    }

    @Test
    @DisplayName("CMS-04 Controller UTCID03 [B]: Negative page parameter returns 400 VALIDATION_FAILED")
    void listMyCommissions_negativePage_returns400() throws Exception {
        mockMvc.perform(get(MY_COMMISSIONS_PATH)
                        .param("page", "-1")
                        .with(SecurityMockMvcRequestPostProcessors.user("10").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listCustomerCommissionsUseCase);
    }

    @Test
    @DisplayName("CMS-04 Controller UTCID04 [B]: Invalid sortDirection parameter returns 400 VALIDATION_FAILED")
    void listMyCommissions_invalidSortDirection_returns400() throws Exception {
        mockMvc.perform(get(MY_COMMISSIONS_PATH)
                        .param("sortDirection", "RANDOM")
                        .with(SecurityMockMvcRequestPostProcessors.user("10").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listCustomerCommissionsUseCase);
    }

    @Test
    @DisplayName("CMS-04 Controller UTCID05 [B]: Page size exceeding 100 returns 400 VALIDATION_FAILED")
    void listMyCommissions_sizeExceedsMax_returns400() throws Exception {
        mockMvc.perform(get(MY_COMMISSIONS_PATH)
                        .param("size", "101")
                        .with(SecurityMockMvcRequestPostProcessors.user("10").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listCustomerCommissionsUseCase);
    }

    @Test
    @DisplayName("CMS-04 Controller UTCID06 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void listMyCommissions_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(MY_COMMISSIONS_PATH)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(listCustomerCommissionsUseCase);
    }

    @Test
    @DisplayName("CMS-04 Controller UTCID07 [A]: Non-customer role (STAFF) returns 403 FORBIDDEN")
    void listMyCommissions_staffRole_returns403() throws Exception {
        mockMvc.perform(get(MY_COMMISSIONS_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("2").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(listCustomerCommissionsUseCase);
    }

    @Test
    @DisplayName("CMS-04 Controller UTCID08 [A]: Non-numeric customer principal in token returns 401 INVALID_CREDENTIALS")
    void listMyCommissions_nonNumericPrincipal_returns401() throws Exception {
        mockMvc.perform(get(MY_COMMISSIONS_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("not-a-number").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        verifyNoInteractions(listCustomerCommissionsUseCase);
    }

    @Test
    @DisplayName("CMS-04 Controller UTCID09 [A]: Unexpected runtime exception returns 500 INTERNAL_SERVER_ERROR")
    void listMyCommissions_unexpectedException_returns500() throws Exception {
        when(listCustomerCommissionsUseCase.execute(eq(10L), any(CustomerCommissionFilterRequest.class)))
                .thenThrow(new RuntimeException("Database connection lost"));

        mockMvc.perform(get(MY_COMMISSIONS_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("10").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }
}
