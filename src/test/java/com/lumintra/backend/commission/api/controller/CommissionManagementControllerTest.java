package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.request.StaffCommissionQueueFilterRequest;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse.StaffCommissionQueueItem;
import com.lumintra.backend.commission.application.GetStaffCommissionQueueUseCase;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommissionManagementController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        GlobalExceptionHandler.class
})
@ActiveProfiles("test")
class CommissionManagementControllerTest {

    private static final String QUEUE_PATH = "/api/management/commissions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetStaffCommissionQueueUseCase getStaffCommissionQueueUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private StaffCommissionQueueResponse createSampleQueueResponse() {
        StaffCommissionQueueItem item = StaffCommissionQueueItem.builder()
                .commissionId(105L)
                .customerId(12L)
                .customerName("Nguyen Van A")
                .customerEmail("nguyenvana@gmail.com")
                .title("Cyberpunk Neon Cat Animation")
                .tierCode("ADVANCED")
                .targetMatrixDimensions("32x64")
                .status(CommissionStatus.PENDING_REVIEW)
                .initialPrice(new BigDecimal("250000.00"))
                .extraRevisionFee(BigDecimal.ZERO)
                .totalPrice(new BigDecimal("250000.00"))
                .revisionsUsed(0)
                .maxRevisions(2)
                .assignedStaffId(3L)
                .assignedStaffName("Tran Thi Staff")
                .assignedDesignerId(null)
                .assignedDesignerName(null)
                .isSlaOverdue(false)
                .quoteExpiresAt(null)
                .slaDueAt(Instant.parse("2026-11-18T10:00:00Z"))
                .createdAt(Instant.parse("2026-11-17T08:00:00Z"))
                .updatedAt(Instant.parse("2026-11-17T08:00:00Z"))
                .build();

        PaginationMeta meta = PaginationMeta.builder()
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        return StaffCommissionQueueResponse.builder()
                .items(List.of(item))
                .meta(meta)
                .build();
    }

    @Test
    @DisplayName("CMS-06 Controller UTCID01 [N]: Authenticated STAFF retrieves commission queue successfully (200 OK)")
    void getStaffCommissionQueue_asStaff_returns200OkWithItemsAndPagination() throws Exception {
        when(getStaffCommissionQueueUseCase.execute(any(StaffCommissionQueueFilterRequest.class)))
                .thenReturn(createSampleQueueResponse());

        mockMvc.perform(get(QUEUE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].commissionId").value(105))
                .andExpect(jsonPath("$.data[0].customerName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data[0].customerEmail").value("nguyenvana@gmail.com"))
                .andExpect(jsonPath("$.data[0].title").value("Cyberpunk Neon Cat Animation"))
                .andExpect(jsonPath("$.data[0].tierCode").value("ADVANCED"))
                .andExpect(jsonPath("$.data[0].targetMatrixDimensions").value("32x64"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data[0].totalPrice").value(250000.00))
                .andExpect(jsonPath("$.data[0].assignedStaffName").value("Tran Thi Staff"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(1));

        ArgumentCaptor<StaffCommissionQueueFilterRequest> captor = ArgumentCaptor.forClass(StaffCommissionQueueFilterRequest.class);
        verify(getStaffCommissionQueueUseCase).execute(captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(0);
        assertThat(captor.getValue().size()).isEqualTo(20);
        assertThat(captor.getValue().sortDirection()).isEqualTo("DESC");
        assertThat(captor.getValue().status()).isNull();
    }

    @Test
    @DisplayName("CMS-06 Controller UTCID02 [N]: Authenticated MANAGER retrieves commission queue successfully (200 OK)")
    void getStaffCommissionQueue_asManager_returns200Ok() throws Exception {
        when(getStaffCommissionQueueUseCase.execute(any(StaffCommissionQueueFilterRequest.class)))
                .thenReturn(createSampleQueueResponse());

        mockMvc.perform(get(QUEUE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].commissionId").value(105));
    }

    @Test
    @DisplayName("CMS-06 Controller UTCID03 [N]: Authenticated ADMIN retrieves commission queue successfully (200 OK)")
    void getStaffCommissionQueue_asAdmin_returns200Ok() throws Exception {
        when(getStaffCommissionQueueUseCase.execute(any(StaffCommissionQueueFilterRequest.class)))
                .thenReturn(createSampleQueueResponse());

        mockMvc.perform(get(QUEUE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("99").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].commissionId").value(105));
    }

    @Test
    @DisplayName("CMS-06 Controller UTCID04 [A]: Unauthenticated access is rejected (401 UNAUTHORIZED)")
    void getStaffCommissionQueue_unauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get(QUEUE_PATH)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(getStaffCommissionQueueUseCase);
    }

    @Test
    @DisplayName("CMS-06 Controller UTCID05 [A]: Authenticated CUSTOMER is forbidden from accessing staff queue (403 FORBIDDEN)")
    void getStaffCommissionQueue_asCustomer_returns403Forbidden() throws Exception {
        mockMvc.perform(get(QUEUE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("10").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(getStaffCommissionQueueUseCase);
    }

    @Test
    @DisplayName("CMS-06 Controller UTCID06 [A]: Authenticated DESIGNER is forbidden from accessing staff queue (403 FORBIDDEN)")
    void getStaffCommissionQueue_asDesigner_returns403Forbidden() throws Exception {
        mockMvc.perform(get(QUEUE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("20").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(getStaffCommissionQueueUseCase);
    }

    @Test
    @DisplayName("CMS-06 Controller UTCID07 [B]: Filtering by specific status passes parameter to useCase")
    void getStaffCommissionQueue_withStatusFilter_passesStatusToUseCase() throws Exception {
        when(getStaffCommissionQueueUseCase.execute(any(StaffCommissionQueueFilterRequest.class)))
                .thenReturn(createSampleQueueResponse());

        mockMvc.perform(get(QUEUE_PATH)
                        .param("status", "IN_PROGRESS")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sortDirection", "ASC")
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        ArgumentCaptor<StaffCommissionQueueFilterRequest> captor = ArgumentCaptor.forClass(StaffCommissionQueueFilterRequest.class);
        verify(getStaffCommissionQueueUseCase).execute(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(CommissionStatus.IN_PROGRESS);
        assertThat(captor.getValue().page()).isEqualTo(1);
        assertThat(captor.getValue().size()).isEqualTo(10);
        assertThat(captor.getValue().sortDirection()).isEqualTo("ASC");
    }

    @Test
    @DisplayName("CMS-06 Controller UTCID08 [B]: Negative page parameter triggers validation failure (400 BAD_REQUEST)")
    void getStaffCommissionQueue_negativePage_returns400BadRequest() throws Exception {
        mockMvc.perform(get(QUEUE_PATH)
                        .param("page", "-1")
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(getStaffCommissionQueueUseCase);
    }

    @Test
    @DisplayName("CMS-06 Controller UTCID09 [B]: Zero size parameter triggers validation failure (400 BAD_REQUEST)")
    void getStaffCommissionQueue_zeroSize_returns400BadRequest() throws Exception {
        mockMvc.perform(get(QUEUE_PATH)
                        .param("size", "0")
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(getStaffCommissionQueueUseCase);
    }

    @Test
    @DisplayName("CMS-06 Controller UTCID10 [B]: Size exceeding 100 triggers validation failure (400 BAD_REQUEST)")
    void getStaffCommissionQueue_sizeExceeding100_returns400BadRequest() throws Exception {
        mockMvc.perform(get(QUEUE_PATH)
                        .param("size", "101")
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(getStaffCommissionQueueUseCase);
    }

    @Test
    @DisplayName("CMS-06 Controller UTCID11 [B]: Invalid sortDirection triggers validation failure (400 BAD_REQUEST)")
    void getStaffCommissionQueue_invalidSortDirection_returns400BadRequest() throws Exception {
        mockMvc.perform(get(QUEUE_PATH)
                        .param("sortDirection", "RANDOM")
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(getStaffCommissionQueueUseCase);
    }
}
