package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import org.junit.jupiter.api.BeforeEach;

import java.util.Set;

import com.lumintra.backend.commission.api.request.AssignCommissionRequest;
import com.lumintra.backend.commission.api.request.DesignerCommissionQueueFilterRequest;
import com.lumintra.backend.commission.api.request.RejectCommissionDraftRequest;
import com.lumintra.backend.commission.api.request.RejectCommissionRequest;
import com.lumintra.backend.commission.api.request.StaffCommissionQueueFilterRequest;
import com.lumintra.backend.commission.api.request.UploadCommissionDraftRequest;
import com.lumintra.backend.commission.api.response.ApproveCommissionResponse;
import com.lumintra.backend.commission.api.response.AssignCommissionResponse;
import com.lumintra.backend.commission.api.response.DesignerCommissionQueueResponse;
import com.lumintra.backend.commission.api.response.DesignerCommissionQueueResponse.DesignerCommissionQueueItem;
import com.lumintra.backend.commission.api.response.ManagementCommissionDetailResponse;
import com.lumintra.backend.commission.api.response.ManagementCommissionDetailResponse.ManagementCommissionAttachmentItem;
import com.lumintra.backend.commission.api.response.ManagementCommissionDetailResponse.ManagementCommissionSubmissionItem;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.api.response.PostDeliverableCardResponse;
import com.lumintra.backend.commission.api.response.RejectCommissionDraftResponse;
import com.lumintra.backend.commission.api.response.RejectCommissionResponse;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse.StaffCommissionQueueItem;
import com.lumintra.backend.commission.api.response.UploadCommissionDraftResponse;
import com.lumintra.backend.commission.application.ApproveCommissionRequestUseCase;
import com.lumintra.backend.commission.application.AssignCommissionToDesignerUseCase;
import com.lumintra.backend.commission.application.GetDesignerCommissionQueueUseCase;
import com.lumintra.backend.commission.application.GetManagementCommissionDetailUseCase;
import com.lumintra.backend.commission.application.GetStaffCommissionQueueUseCase;
import com.lumintra.backend.commission.application.PostDeliverableCardUseCase;
import com.lumintra.backend.commission.application.RejectCommissionDraftUseCase;
import com.lumintra.backend.commission.application.RejectCommissionRequestUseCase;
import com.lumintra.backend.commission.application.UploadCommissionDraftUseCase;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.CommissionStaffLockException;
import com.lumintra.backend.shared.exception.commission.CommissionSubmissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import com.lumintra.backend.shared.exception.media.InvalidMediaUrlException;
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
import org.springframework.security.access.AccessDeniedException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @MockitoBean
    private com.lumintra.backend.iam.domain.service.EmployeeLookupService employeeLookupService;

    @MockitoBean
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @BeforeEach
    void resolveTestEmployeeIds() {
        when(internalEmployeeAccessService.requireActiveEmployeeId(any(String.class), any()))
                .thenAnswer(call -> Long.valueOf(call.<String>getArgument(0)));
    }

    private static final String QUEUE_PATH = "/api/management/commissions";
    private static final String ASSIGNED_QUEUE_PATH = "/api/management/commissions/assigned";
    private static final String DETAIL_PATH = "/api/management/commissions/{id}";
    private static final String APPROVE_PATH = "/api/management/commissions/{id}/approve";
    private static final String ASSIGN_PATH = "/api/management/commissions/{id}/assign";
    private static final String REJECT_PATH = "/api/management/commissions/{id}/reject";
    private static final String DRAFTS_PATH = "/api/management/commissions/{id}/drafts";
    private static final String REVIEWS_PATH = "/api/management/commissions/{id}/reviews";
    private static final String DELIVERABLE_CARD_PATH = "/api/management/commissions/{id}/deliverable-card";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetStaffCommissionQueueUseCase getStaffCommissionQueueUseCase;

    @MockitoBean
    private GetManagementCommissionDetailUseCase getManagementCommissionDetailUseCase;

    @MockitoBean
    private ApproveCommissionRequestUseCase approveCommissionRequestUseCase;

    @MockitoBean
    private AssignCommissionToDesignerUseCase assignCommissionToDesignerUseCase;

    @MockitoBean
    private GetDesignerCommissionQueueUseCase getDesignerCommissionQueueUseCase;

    @MockitoBean
    private RejectCommissionRequestUseCase rejectCommissionRequestUseCase;

    @MockitoBean
    private UploadCommissionDraftUseCase uploadCommissionDraftUseCase;

    @MockitoBean
    private RejectCommissionDraftUseCase rejectCommissionDraftUseCase;

    @MockitoBean
    private PostDeliverableCardUseCase postDeliverableCardUseCase;

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

    private ManagementCommissionDetailResponse createSampleDetailResponse() {
        return ManagementCommissionDetailResponse.builder()
                .commissionId(105L)
                .customerId(12L)
                .customerName("Nguyen Van A")
                .customerEmail("nguyenvana@gmail.com")
                .customerPhone("0901234567")
                .title("Cyberpunk Neon Cat Animation")
                .description("Animated neon cat blinking")
                .tierCode("ADVANCED")
                .targetMatrixDimensions("32x64")
                .priceBreakdownJson("{\"tierCode\":\"ADVANCED\"}")
                .initialPrice(new BigDecimal("250000.00"))
                .extraRevisionFee(BigDecimal.ZERO)
                .totalPrice(new BigDecimal("250000.00"))
                .revisionsUsed(1)
                .maxRevisions(2)
                .status(CommissionStatus.INTERNAL_REVIEW)
                .assignedStaffId(3L)
                .assignedStaffName("Tran Thi Staff")
                .assignedDesignerId(5L)
                .assignedDesignerName("Le Van Designer")
                .isSlaOverdue(false)
                .activePaymentCode(null)
                .approvedSubmissionId(null)
                .rejectionReason(null)
                .chatActive(true)
                .quoteExpiresAt(null)
                .slaDueAt(Instant.parse("2026-11-18T10:00:00Z"))
                .autoCompleteAt(null)
                .createdAt(Instant.parse("2026-11-17T08:00:00Z"))
                .updatedAt(Instant.parse("2026-11-17T08:20:00Z"))
                .attachments(List.of(
                        ManagementCommissionAttachmentItem.builder()
                                .attachmentId(201L)
                                .imageUrl("https://cdn.lumintra.shop/commissions/ref-cat.png")
                                .caption("Cat ears")
                                .displayOrder(0)
                                .createdAt(Instant.parse("2026-11-17T08:00:00Z"))
                                .build()
                ))
                .submissions(List.of(
                        ManagementCommissionSubmissionItem.builder()
                                .submissionId(901L)
                                .designerId(5L)
                                .designerName("Le Van Designer")
                                .versionNumber(1)
                                .imageUrl("https://cdn.lumintra.shop/commissions/draft-v1.gif")
                                .designerNote("Initial draft")
                                .status(CommissionSubmissionStatus.STAFF_REJECTED)
                                .reviewerStaffId(3L)
                                .reviewerStaffName("Tran Thi Staff")
                                .staffReviewNote("Blinking too fast")
                                .reviewedAt(Instant.parse("2026-11-17T08:15:00Z"))
                                .deliveredAt(null)
                                .createdAt(Instant.parse("2026-11-17T08:10:00Z"))
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("CMS-07 Controller UTCID01 [N]: Authenticated STAFF retrieves management commission details (200 OK)")
    void getManagementCommissionDetail_asStaff_returns200Ok() throws Exception {
        when(getManagementCommissionDetailUseCase.execute(eq(105L), eq(3L), eq(true)))
                .thenReturn(createSampleDetailResponse());

        mockMvc.perform(get(DETAIL_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionId").value(105))
                .andExpect(jsonPath("$.data.customerName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.customerEmail").value("nguyenvana@gmail.com"))
                .andExpect(jsonPath("$.data.customerPhone").value("0901234567"))
                .andExpect(jsonPath("$.data.title").value("Cyberpunk Neon Cat Animation"))
                .andExpect(jsonPath("$.data.tierCode").value("ADVANCED"))
                .andExpect(jsonPath("$.data.status").value("INTERNAL_REVIEW"))
                .andExpect(jsonPath("$.data.assignedStaffName").value("Tran Thi Staff"))
                .andExpect(jsonPath("$.data.assignedDesignerName").value("Le Van Designer"))
                .andExpect(jsonPath("$.data.attachments[0].attachmentId").value(201))
                .andExpect(jsonPath("$.data.submissions[0].submissionId").value(901))
                .andExpect(jsonPath("$.data.submissions[0].staffReviewNote").value("Blinking too fast"));

        verify(getManagementCommissionDetailUseCase).execute(eq(105L), eq(3L), eq(true));
    }

    @Test
    @DisplayName("CMS-07 Controller UTCID02 [N]: Authenticated MANAGER retrieves management commission details (200 OK)")
    void getManagementCommissionDetail_asManager_returns200Ok() throws Exception {
        when(getManagementCommissionDetailUseCase.execute(eq(105L), eq(1L), eq(true)))
                .thenReturn(createSampleDetailResponse());

        mockMvc.perform(get(DETAIL_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionId").value(105));

        verify(getManagementCommissionDetailUseCase).execute(eq(105L), eq(1L), eq(true));
    }

    @Test
    @DisplayName("CMS-07 Controller UTCID03 [N]: Authenticated ADMIN retrieves management commission details (200 OK)")
    void getManagementCommissionDetail_asAdmin_returns200Ok() throws Exception {
        when(getManagementCommissionDetailUseCase.execute(eq(105L), eq(99L), eq(true)))
                .thenReturn(createSampleDetailResponse());

        mockMvc.perform(get(DETAIL_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("99").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionId").value(105));

        verify(getManagementCommissionDetailUseCase).execute(eq(105L), eq(99L), eq(true));
    }

    @Test
    @DisplayName("CMS-07 Controller UTCID04 [N]: Assigned DESIGNER retrieves details of assigned ticket (200 OK)")
    void getManagementCommissionDetail_asAssignedDesigner_returns200Ok() throws Exception {
        when(getManagementCommissionDetailUseCase.execute(eq(105L), eq(5L), eq(false)))
                .thenReturn(createSampleDetailResponse());

        mockMvc.perform(get(DETAIL_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("5").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionId").value(105));

        verify(getManagementCommissionDetailUseCase).execute(eq(105L), eq(5L), eq(false));
    }

    @Test
    @DisplayName("CMS-07 Controller UTCID05 [A]: Unauthenticated access is rejected (401 UNAUTHORIZED)")
    void getManagementCommissionDetail_unauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get(DETAIL_PATH, 105L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(getManagementCommissionDetailUseCase);
    }

    @Test
    @DisplayName("CMS-07 Controller UTCID06 [A]: Authenticated CUSTOMER is forbidden from management detail (403 FORBIDDEN)")
    void getManagementCommissionDetail_asCustomer_returns403Forbidden() throws Exception {
        mockMvc.perform(get(DETAIL_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("12").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(getManagementCommissionDetailUseCase);
    }

    @Test
    @DisplayName("CMS-07 Controller UTCID07 [A]: Unassigned DESIGNER is forbidden (403 FORBIDDEN)")
    void getManagementCommissionDetail_asUnassignedDesigner_returns403Forbidden() throws Exception {
        when(getManagementCommissionDetailUseCase.execute(eq(105L), eq(99L), eq(false)))
                .thenThrow(new AccessDeniedException("You are not the designer assigned to this commission ticket."));

        mockMvc.perform(get(DETAIL_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("99").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("CMS-07 Controller UTCID08 [A]: Non-existent commission ID returns 404 NOT_FOUND")
    void getManagementCommissionDetail_notFound_returns404NotFound() throws Exception {
        when(getManagementCommissionDetailUseCase.execute(eq(999L), eq(3L), eq(true)))
                .thenThrow(new CommissionNotFoundException("Commission request not found with id: 999"));

        mockMvc.perform(get(DETAIL_PATH, 999L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMISSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("CMS-07 Controller UTCID09 [B]: Non-positive id parameter triggers validation failure (400 BAD_REQUEST)")
    void getManagementCommissionDetail_nonPositiveId_returns400BadRequest() throws Exception {
        mockMvc.perform(get(DETAIL_PATH, 0L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(getManagementCommissionDetailUseCase);
    }

    // =========================================================================
    // CMS-09: Approve Commission Request Tests (POST /api/management/commissions/{id}/approve)
    // =========================================================================

    private ApproveCommissionResponse createSampleApproveResponse() {
        PaymentSessionResponse paymentSession = PaymentSessionResponse.builder()
                .paymentId(501L)
                .paymentCode("LMTCMS105")
                .targetType(PaymentTargetType.COMMISSION_INITIAL)
                .targetId(105L)
                .gateway("SEPAY")
                .qrUrl("https://qr.sepay.vn/img?des=LMTCMS105")
                .bankName("MBBank")
                .accountNumber("0123456789")
                .accountName("LUMINTRA")
                .amount(BigDecimal.valueOf(420000.00))
                .status(PaymentStatus.PENDING)
                .expiresAt(Instant.parse("2026-11-20T11:00:00Z"))
                .createdAt(Instant.parse("2026-11-17T11:00:00Z"))
                .build();

        return ApproveCommissionResponse.builder()
                .commissionId(105L)
                .title("Cyberpunk Neon Cat Animation")
                .status(CommissionStatus.PENDING_PAYMENT)
                .assignedStaffId(3L)
                .quoteExpiresAt(Instant.parse("2026-11-20T11:00:00Z"))
                .activePaymentCode("LMTCMS105")
                .payableAmount(BigDecimal.valueOf(420000.00))
                .paymentSession(paymentSession)
                .updatedAt(Instant.parse("2026-11-17T11:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("CMS-09 Controller UTCID01 [N]: Staff approves commission successfully (200 OK)")
    void approveCommission_asStaff_returns200Ok() throws Exception {
        ApproveCommissionResponse response = createSampleApproveResponse();
        when(approveCommissionRequestUseCase.execute(eq(105L), eq(3L), eq(false))).thenReturn(response);

        mockMvc.perform(post(APPROVE_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionId").value(105))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.assignedStaffId").value(3))
                .andExpect(jsonPath("$.data.activePaymentCode").value("LMTCMS105"))
                .andExpect(jsonPath("$.data.payableAmount").value(420000.00))
                .andExpect(jsonPath("$.data.paymentSession.paymentCode").value("LMTCMS105"))
                .andExpect(jsonPath("$.data.paymentSession.qrUrl").value("https://qr.sepay.vn/img?des=LMTCMS105"));
    }

    @Test
    @DisplayName("CMS-09 Controller UTCID02 [N]: Manager approves commission successfully with privileged override flag (200 OK)")
    void approveCommission_asManager_returns200Ok() throws Exception {
        ApproveCommissionResponse response = createSampleApproveResponse();
        when(approveCommissionRequestUseCase.execute(eq(105L), eq(2L), eq(true))).thenReturn(response);

        mockMvc.perform(post(APPROVE_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("2").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionId").value(105))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
    }

    @Test
    @DisplayName("CMS-09 Controller UTCID03 [N]: Admin approves commission successfully with privileged override flag (200 OK)")
    void approveCommission_asAdmin_returns200Ok() throws Exception {
        ApproveCommissionResponse response = createSampleApproveResponse();
        when(approveCommissionRequestUseCase.execute(eq(105L), eq(1L), eq(true))).thenReturn(response);

        mockMvc.perform(post(APPROVE_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionId").value(105));
    }

    @Test
    @DisplayName("CMS-09 Controller UTCID04 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void approveCommission_unauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(post(APPROVE_PATH, 105L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(approveCommissionRequestUseCase);
    }

    @Test
    @DisplayName("CMS-09 Controller UTCID05 [A]: Unauthorized role (CUSTOMER) receives 403 FORBIDDEN")
    void approveCommission_customerRole_returns403Forbidden() throws Exception {
        mockMvc.perform(post(APPROVE_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("12").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(approveCommissionRequestUseCase);
    }

    @Test
    @DisplayName("CMS-09 Controller UTCID06 [A]: Commission staff lock violation returns 403 FORBIDDEN (COMMISSION_STAFF_LOCKED)")
    void approveCommission_staffLockViolation_returns403Forbidden() throws Exception {
        when(approveCommissionRequestUseCase.execute(eq(105L), eq(3L), eq(false)))
                .thenThrow(new CommissionStaffLockException("Commission request 105 is locked by assigned staff 7"));

        mockMvc.perform(post(APPROVE_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMISSION_STAFF_LOCKED"))
                .andExpect(jsonPath("$.message").value("Commission request 105 is locked by assigned staff 7"));
    }

    @Test
    @DisplayName("CMS-09 Controller UTCID07 [A]: Commission ticket not found returns 404 NOT_FOUND (COMMISSION_NOT_FOUND)")
    void approveCommission_notFound_returns404NotFound() throws Exception {
        when(approveCommissionRequestUseCase.execute(eq(999L), eq(3L), eq(false)))
                .thenThrow(new CommissionNotFoundException("Commission request not found with id: 999"));

        mockMvc.perform(post(APPROVE_PATH, 999L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMISSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("CMS-09 Controller UTCID08 [A]: Invalid commission status transition returns 409 CONFLICT (INVALID_COMMISSION_STATUS_TRANSITION)")
    void approveCommission_invalidStatus_returns409Conflict() throws Exception {
        when(approveCommissionRequestUseCase.execute(eq(105L), eq(3L), eq(false)))
                .thenThrow(new InvalidCommissionStatusTransitionException(
                        "Cannot approve commission in status IN_PROGRESS (must be PENDING_REVIEW)"));

        mockMvc.perform(post(APPROVE_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_COMMISSION_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value("Cannot approve commission in status IN_PROGRESS (must be PENDING_REVIEW)"));
    }

    @Test
    @DisplayName("CMS-09 Controller UTCID09 [B]: Non-positive commission ID parameter returns 400 BAD_REQUEST (VALIDATION_FAILED)")
    void approveCommission_nonPositiveId_returns400BadRequest() throws Exception {
        mockMvc.perform(post(APPROVE_PATH, 0L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(approveCommissionRequestUseCase);
    }

    @Test
    @DisplayName("CMS-13 Controller UTCID07 [N]: Valid assign request returns 200 OK with AssignCommissionResponse")
    void assignCommission_validRequest_returns200Ok() throws Exception {
        when(internalEmployeeAccessService.requireActiveEmployeeId("103", Set.of("STAFF", "MANAGER", "ADMIN")))
                .thenReturn(3L);
        AssignCommissionResponse response = AssignCommissionResponse.builder()
                .commissionId(101L)
                .status(CommissionStatus.IN_PROGRESS)
                .assignedStaffId(3L)
                .assignedDesignerId(5L)
                .slaDueAt(Instant.parse("2026-11-20T12:00:00Z"))
                .updatedAt(Instant.parse("2026-11-17T12:00:00Z"))
                .build();

        when(assignCommissionToDesignerUseCase.execute(eq(101L), any(AssignCommissionRequest.class), eq(3L), eq(false)))
                .thenReturn(response);

        String requestBody = """
                {
                    "designerId": 5
                }
                """;

        mockMvc.perform(post(ASSIGN_PATH, 101L)
                        .with(SecurityMockMvcRequestPostProcessors.user("103").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionId").value(101))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.assignedStaffId").value(3))
                .andExpect(jsonPath("$.data.assignedDesignerId").value(5))
                .andExpect(jsonPath("$.data.slaDueAt").value("2026-11-20T12:00:00Z"));

        ArgumentCaptor<AssignCommissionRequest> captor = ArgumentCaptor.forClass(AssignCommissionRequest.class);
        verify(assignCommissionToDesignerUseCase).execute(eq(101L), captor.capture(), eq(3L), eq(false));
        assertThat(captor.getValue().designerId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("CMS-13 Controller UTCID08 [B]: Null or non-positive designerId returns 400 BAD_REQUEST (VALIDATION_FAILED)")
    void assignCommission_invalidDesignerId_returns400BadRequest() throws Exception {
        String invalidBody = """
                {
                    "designerId": 0
                }
                """;

        mockMvc.perform(post(ASSIGN_PATH, 101L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(assignCommissionToDesignerUseCase);
    }

    @Test
    @DisplayName("CMS-13 Controller UTCID09 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void assignCommission_unauthenticated_returns401Unauthorized() throws Exception {
        String requestBody = """
                {
                    "designerId": 5
                }
                """;

        mockMvc.perform(post(ASSIGN_PATH, 101L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(assignCommissionToDesignerUseCase);
    }

    @Test
    @DisplayName("CMS-13 Controller UTCID10 [A]: Caller with CUSTOMER role returns 403 FORBIDDEN")
    void assignCommission_customerRole_returns403Forbidden() throws Exception {
        String requestBody = """
                {
                    "designerId": 5
                }
                """;

        mockMvc.perform(post(ASSIGN_PATH, 101L)
                        .with(SecurityMockMvcRequestPostProcessors.user("501").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(assignCommissionToDesignerUseCase);
    }

    private DesignerCommissionQueueResponse createSampleDesignerQueueResponse() {
        DesignerCommissionQueueItem item = DesignerCommissionQueueItem.builder()
                .commissionId(101L)
                .customerId(12L)
                .customerName("Alice Customer")
                .customerEmail("alice@example.com")
                .title("Cyberpunk Neon Cat Animation")
                .tierCode("ADVANCED")
                .targetMatrixDimensions("32x64")
                .status(CommissionStatus.IN_PROGRESS)
                .revisionsUsed(0)
                .maxRevisions(2)
                .assignedStaffId(3L)
                .assignedStaffName("Staff John")
                .assignedDesignerId(5L)
                .assignedDesignerName("Designer David")
                .isSlaOverdue(false)
                .slaDueAt(Instant.parse("2026-11-20T12:00:00Z"))
                .createdAt(Instant.parse("2026-11-17T10:00:00Z"))
                .updatedAt(Instant.parse("2026-11-17T12:00:00Z"))
                .build();

        PaginationMeta meta = PaginationMeta.builder()
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        return DesignerCommissionQueueResponse.builder()
                .items(List.of(item))
                .meta(meta)
                .build();
    }

    @Test
    @DisplayName("CMS-14 Controller UTCID01 [N]: Authenticated DESIGNER retrieves assigned queue successfully (200 OK)")
    void getDesignerCommissionQueue_validDesigner_returns200OkWithItemsAndPagination() throws Exception {
        when(getDesignerCommissionQueueUseCase.execute(eq(5L), any(DesignerCommissionQueueFilterRequest.class)))
                .thenReturn(createSampleDesignerQueueResponse());

        mockMvc.perform(get(ASSIGNED_QUEUE_PATH)
                        .param("status", "IN_PROGRESS")
                        .param("sortDirection", "DESC")
                        .param("page", "0")
                        .param("size", "20")
                        .with(SecurityMockMvcRequestPostProcessors.user("5").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].commissionId").value(101))
                .andExpect(jsonPath("$.data[0].customerId").value(12))
                .andExpect(jsonPath("$.data[0].customerName").value("Alice Customer"))
                .andExpect(jsonPath("$.data[0].customerEmail").value("alice@example.com"))
                .andExpect(jsonPath("$.data[0].title").value("Cyberpunk Neon Cat Animation"))
                .andExpect(jsonPath("$.data[0].tierCode").value("ADVANCED"))
                .andExpect(jsonPath("$.data[0].targetMatrixDimensions").value("32x64"))
                .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data[0].revisionsUsed").value(0))
                .andExpect(jsonPath("$.data[0].maxRevisions").value(2))
                .andExpect(jsonPath("$.data[0].assignedStaffId").value(3))
                .andExpect(jsonPath("$.data[0].assignedStaffName").value("Staff John"))
                .andExpect(jsonPath("$.data[0].assignedDesignerId").value(5))
                .andExpect(jsonPath("$.data[0].assignedDesignerName").value("Designer David"))
                .andExpect(jsonPath("$.data[0].isSlaOverdue").value(false))
                .andExpect(jsonPath("$.data[0].slaDueAt").value("2026-11-20T12:00:00Z"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(1));

        ArgumentCaptor<DesignerCommissionQueueFilterRequest> captor = ArgumentCaptor.forClass(DesignerCommissionQueueFilterRequest.class);
        verify(getDesignerCommissionQueueUseCase).execute(eq(5L), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(CommissionStatus.IN_PROGRESS);
        assertThat(captor.getValue().sortDirection()).isEqualTo("DESC");
        assertThat(captor.getValue().page()).isEqualTo(0);
        assertThat(captor.getValue().size()).isEqualTo(20);
    }

    @Test
    @DisplayName("CMS-14 Controller UTCID02 [B]: Negative page or zero size returns 400 BAD_REQUEST (VALIDATION_FAILED)")
    void getDesignerCommissionQueue_invalidPaginationOrFilter_returns400BadRequest() throws Exception {
        mockMvc.perform(get(ASSIGNED_QUEUE_PATH)
                        .param("page", "-1")
                        .param("size", "0")
                        .with(SecurityMockMvcRequestPostProcessors.user("5").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(getDesignerCommissionQueueUseCase);
    }

    @Test
    @DisplayName("CMS-14 Controller UTCID03 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void getDesignerCommissionQueue_unauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get(ASSIGNED_QUEUE_PATH)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(getDesignerCommissionQueueUseCase);
    }

    @Test
    @DisplayName("CMS-14 Controller UTCID04 [A]: Caller with STAFF or CUSTOMER role returns 403 FORBIDDEN")
    void getDesignerCommissionQueue_unauthorizedRoleStaffOrCustomer_returns403Forbidden() throws Exception {
        mockMvc.perform(get(ASSIGNED_QUEUE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(getDesignerCommissionQueueUseCase);
    }

    @Test
    @DisplayName("CMS-08 Controller UTCID01 [N]: Staff rejects commission in PENDING_REVIEW -> returns 200 OK with RejectCommissionResponse")
    void rejectCommission_validStaff_returns200Ok() throws Exception {
        RejectCommissionResponse response = RejectCommissionResponse.builder()
                .commissionId(105L)
                .status(CommissionStatus.CANCELLED)
                .assignedStaffId(3L)
                .rejectionReason("Artwork request exceeds hardware matrix limits.")
                .updatedAt(Instant.parse("2026-11-17T14:30:00Z"))
                .build();

        when(rejectCommissionRequestUseCase.execute(eq(105L), any(RejectCommissionRequest.class), eq(3L), eq(false)))
                .thenReturn(response);

        String requestBody = """
                {
                    "rejectionReason": "Artwork request exceeds hardware matrix limits."
                }
                """;

        mockMvc.perform(post(REJECT_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionId").value(105))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.assignedStaffId").value(3))
                .andExpect(jsonPath("$.data.rejectionReason").value("Artwork request exceeds hardware matrix limits."));

        ArgumentCaptor<RejectCommissionRequest> captor = ArgumentCaptor.forClass(RejectCommissionRequest.class);
        verify(rejectCommissionRequestUseCase).execute(eq(105L), captor.capture(), eq(3L), eq(false));
        assertThat(captor.getValue().rejectionReason()).isEqualTo("Artwork request exceeds hardware matrix limits.");
    }

    @Test
    @DisplayName("CMS-08 Controller UTCID02 [B]: Blank rejectionReason returns 400 BAD_REQUEST (VALIDATION_FAILED)")
    void rejectCommission_blankRejectionReason_returns400BadRequest() throws Exception {
        String invalidBody = """
                {
                    "rejectionReason": "   "
                }
                """;

        mockMvc.perform(post(REJECT_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(rejectCommissionRequestUseCase);
    }

    @Test
    @DisplayName("CMS-08 Controller UTCID03 [B]: Non-positive commission id returns 400 BAD_REQUEST (VALIDATION_FAILED)")
    void rejectCommission_nonPositiveId_returns400BadRequest() throws Exception {
        String requestBody = """
                {
                    "rejectionReason": "Artwork request exceeds hardware matrix limits."
                }
                """;

        mockMvc.perform(post(REJECT_PATH, 0L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(rejectCommissionRequestUseCase);
    }

    @Test
    @DisplayName("CMS-08 Controller UTCID04 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void rejectCommission_unauthenticated_returns401Unauthorized() throws Exception {
        String requestBody = """
                {
                    "rejectionReason": "Artwork request exceeds hardware matrix limits."
                }
                """;

        mockMvc.perform(post(REJECT_PATH, 105L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(rejectCommissionRequestUseCase);
    }

    @Test
    @DisplayName("CMS-08 Controller UTCID05 [A]: Caller with CUSTOMER role returns 403 FORBIDDEN")
    void rejectCommission_customerRole_returns403Forbidden() throws Exception {
        String requestBody = """
                {
                    "rejectionReason": "Artwork request exceeds hardware matrix limits."
                }
                """;

        mockMvc.perform(post(REJECT_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("501").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(rejectCommissionRequestUseCase);
    }

    @Test
    @DisplayName("CMS-08 Controller UTCID06 [A]: Rejection throws CommissionStaffLockException -> returns 403 FORBIDDEN")
    void rejectCommission_lockedByAnotherStaff_returns403Forbidden() throws Exception {
        when(rejectCommissionRequestUseCase.execute(eq(105L), any(RejectCommissionRequest.class), eq(4L), eq(false)))
                .thenThrow(new CommissionStaffLockException("Commission request 105 is locked by assigned staff 3"));

        String requestBody = """
                {
                    "rejectionReason": "Reject attempt by unassigned staff."
                }
                """;

        mockMvc.perform(post(REJECT_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMISSION_STAFF_LOCKED"));
    }

    @Test
    @DisplayName("CMS-08 Controller UTCID07 [A]: Rejection throws CommissionNotFoundException -> returns 404 NOT_FOUND")
    void rejectCommission_notFound_returns404NotFound() throws Exception {
        when(rejectCommissionRequestUseCase.execute(eq(999L), any(RejectCommissionRequest.class), eq(3L), eq(false)))
                .thenThrow(new CommissionNotFoundException("Commission request not found with id: 999"));

        String requestBody = """
                {
                    "rejectionReason": "Not found."
                }
                """;

        mockMvc.perform(post(REJECT_PATH, 999L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMISSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("CMS-08 Controller UTCID08 [A]: Rejection throws InvalidCommissionStatusTransitionException -> returns 409 CONFLICT")
    void rejectCommission_invalidStatus_returns409Conflict() throws Exception {
        when(rejectCommissionRequestUseCase.execute(eq(105L), any(RejectCommissionRequest.class), eq(3L), eq(false)))
                .thenThrow(new InvalidCommissionStatusTransitionException("Cannot reject commission in status PAID (must be PENDING_REVIEW)"));

        String requestBody = """
                {
                    "rejectionReason": "Invalid transition."
                }
                """;

        mockMvc.perform(post(REJECT_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_COMMISSION_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("CMS-15 Controller UTCID01 [N]: Authenticated DESIGNER uploads draft -> returns 201 CREATED with UploadCommissionDraftResponse")
    void uploadCommissionDraft_validDesigner_returns201Created() throws Exception {
        UploadCommissionDraftResponse response = UploadCommissionDraftResponse.builder()
                .submissionId(42L)
                .commissionId(105L)
                .designerId(5L)
                .versionNumber(1)
                .imageUrl("https://cdn.lumintra.shop/commissions/105/drafts/v1-neon-cat.gif")
                .designerNote("First animated draft")
                .submissionStatus(CommissionSubmissionStatus.PENDING_STAFF_REVIEW)
                .commissionStatus(CommissionStatus.INTERNAL_REVIEW)
                .createdAt(Instant.parse("2026-11-17T10:50:00Z"))
                .build();

        when(uploadCommissionDraftUseCase.execute(eq(105L), any(UploadCommissionDraftRequest.class), eq(5L)))
                .thenReturn(response);

        String requestBody = """
                {
                    "imageUrl": "https://cdn.lumintra.shop/commissions/105/drafts/v1-neon-cat.gif",
                    "designerNote": "First animated draft"
                }
                """;

        mockMvc.perform(post(DRAFTS_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("5").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.submissionId").value(42))
                .andExpect(jsonPath("$.data.commissionId").value(105))
                .andExpect(jsonPath("$.data.designerId").value(5))
                .andExpect(jsonPath("$.data.versionNumber").value(1))
                .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.lumintra.shop/commissions/105/drafts/v1-neon-cat.gif"))
                .andExpect(jsonPath("$.data.designerNote").value("First animated draft"))
                .andExpect(jsonPath("$.data.submissionStatus").value("PENDING_STAFF_REVIEW"))
                .andExpect(jsonPath("$.data.commissionStatus").value("INTERNAL_REVIEW"));

        ArgumentCaptor<UploadCommissionDraftRequest> captor = ArgumentCaptor.forClass(UploadCommissionDraftRequest.class);
        verify(uploadCommissionDraftUseCase).execute(eq(105L), captor.capture(), eq(5L));
        assertThat(captor.getValue().imageUrl()).isEqualTo("https://cdn.lumintra.shop/commissions/105/drafts/v1-neon-cat.gif");
        assertThat(captor.getValue().designerNote()).isEqualTo("First animated draft");
    }

    @Test
    @DisplayName("CMS-15 Controller UTCID02 [B]: Blank imageUrl returns 400 BAD_REQUEST (VALIDATION_FAILED)")
    void uploadCommissionDraft_blankImageUrl_returns400BadRequest() throws Exception {
        String invalidBody = """
                {
                    "imageUrl": "   ",
                    "designerNote": "Some note"
                }
                """;

        mockMvc.perform(post(DRAFTS_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("5").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(uploadCommissionDraftUseCase);
    }

    @Test
    @DisplayName("CMS-15 Controller UTCID03 [B]: Non-positive commission id returns 400 BAD_REQUEST (VALIDATION_FAILED)")
    void uploadCommissionDraft_nonPositiveId_returns400BadRequest() throws Exception {
        String requestBody = """
                {
                    "imageUrl": "https://cdn.lumintra.shop/commissions/105/drafts/v1-neon-cat.gif"
                }
                """;

        mockMvc.perform(post(DRAFTS_PATH, 0L)
                        .with(SecurityMockMvcRequestPostProcessors.user("5").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(uploadCommissionDraftUseCase);
    }

    @Test
    @DisplayName("CMS-15 Controller UTCID04 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void uploadCommissionDraft_unauthenticated_returns401Unauthorized() throws Exception {
        String requestBody = """
                {
                    "imageUrl": "https://cdn.lumintra.shop/commissions/105/drafts/v1-neon-cat.gif"
                }
                """;

        mockMvc.perform(post(DRAFTS_PATH, 105L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(uploadCommissionDraftUseCase);
    }

    @Test
    @DisplayName("CMS-15 Controller UTCID05 [A]: Caller with STAFF or CUSTOMER role returns 403 FORBIDDEN")
    void uploadCommissionDraft_nonDesignerRole_returns403Forbidden() throws Exception {
        String requestBody = """
                {
                    "imageUrl": "https://cdn.lumintra.shop/commissions/105/drafts/v1-neon-cat.gif"
                }
                """;

        mockMvc.perform(post(DRAFTS_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(uploadCommissionDraftUseCase);
    }

    @Test
    @DisplayName("CMS-15 Controller UTCID06 [A]: Upload throws CommissionOwnershipMismatchException -> returns 403 FORBIDDEN")
    void uploadCommissionDraft_ownershipMismatch_returns403Forbidden() throws Exception {
        when(uploadCommissionDraftUseCase.execute(eq(105L), any(UploadCommissionDraftRequest.class), eq(8L)))
                .thenThrow(new CommissionOwnershipMismatchException("Commission request 105 is not assigned to designer 8"));

        String requestBody = """
                {
                    "imageUrl": "https://cdn.lumintra.shop/commissions/105/drafts/v1-neon-cat.gif"
                }
                """;

        mockMvc.perform(post(DRAFTS_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("8").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMISSION_OWNERSHIP_MISMATCH"));
    }

    @Test
    @DisplayName("CMS-15 Controller UTCID07 [A]: Upload throws CommissionNotFoundException -> returns 404 NOT_FOUND")
    void uploadCommissionDraft_notFound_returns404NotFound() throws Exception {
        when(uploadCommissionDraftUseCase.execute(eq(999L), any(UploadCommissionDraftRequest.class), eq(5L)))
                .thenThrow(new CommissionNotFoundException("Commission request not found with id: 999"));

        String requestBody = """
                {
                    "imageUrl": "https://cdn.lumintra.shop/commissions/999/drafts/v1-neon-cat.gif"
                }
                """;

        mockMvc.perform(post(DRAFTS_PATH, 999L)
                        .with(SecurityMockMvcRequestPostProcessors.user("5").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMISSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("CMS-15 Controller UTCID08 [A]: Upload throws InvalidCommissionStatusTransitionException -> returns 409 CONFLICT")
    void uploadCommissionDraft_invalidStatus_returns409Conflict() throws Exception {
        when(uploadCommissionDraftUseCase.execute(eq(105L), any(UploadCommissionDraftRequest.class), eq(5L)))
                .thenThrow(new InvalidCommissionStatusTransitionException("Cannot upload draft for commission in status PAID"));

        String requestBody = """
                {
                    "imageUrl": "https://cdn.lumintra.shop/commissions/105/drafts/v1-neon-cat.gif"
                }
                """;

        mockMvc.perform(post(DRAFTS_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("5").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_COMMISSION_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("CMS-15 Controller UTCID09 [A]: Upload throws InvalidMediaUrlException -> returns 400 BAD_REQUEST (VALIDATION_FAILED)")
    void uploadCommissionDraft_invalidMediaUrl_returns400BadRequest() throws Exception {
        when(uploadCommissionDraftUseCase.execute(eq(105L), any(UploadCommissionDraftRequest.class), eq(5L)))
                .thenThrow(new InvalidMediaUrlException("Submitted media URL is invalid or does not belong to authorized CDN / storage domains."));

        String requestBody = """
                {
                    "imageUrl": "https://evil.example.com/malicious.gif"
                }
                """;

        mockMvc.perform(post(DRAFTS_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("5").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("CMS-16 Controller UTCID01 [N]: Authenticated STAFF rejects draft internally -> returns 200 OK with RejectCommissionDraftResponse")
    void rejectCommissionDraft_validStaff_returns200Ok() throws Exception {
        RejectCommissionDraftResponse response = RejectCommissionDraftResponse.builder()
                .commissionId(105L)
                .submissionId(42L)
                .versionNumber(1)
                .submissionStatus(CommissionSubmissionStatus.STAFF_REJECTED)
                .commissionStatus(CommissionStatus.INTERNAL_REVISION_REQUIRED)
                .reviewerStaffId(3L)
                .staffReviewNote("Increase neon edge contrast")
                .revisionsUsed(0)
                .maxRevisions(2)
                .reviewedAt(Instant.parse("2026-11-17T12:15:00Z"))
                .build();

        when(rejectCommissionDraftUseCase.execute(eq(105L), any(RejectCommissionDraftRequest.class), eq(3L), eq(false)))
                .thenReturn(response);

        String requestBody = """
                {
                    "submissionId": 42,
                    "reviewNote": "Increase neon edge contrast"
                }
                """;

        mockMvc.perform(post(REVIEWS_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionId").value(105))
                .andExpect(jsonPath("$.data.submissionId").value(42))
                .andExpect(jsonPath("$.data.versionNumber").value(1))
                .andExpect(jsonPath("$.data.submissionStatus").value("STAFF_REJECTED"))
                .andExpect(jsonPath("$.data.commissionStatus").value("INTERNAL_REVISION_REQUIRED"))
                .andExpect(jsonPath("$.data.reviewerStaffId").value(3))
                .andExpect(jsonPath("$.data.staffReviewNote").value("Increase neon edge contrast"))
                .andExpect(jsonPath("$.data.revisionsUsed").value(0))
                .andExpect(jsonPath("$.data.maxRevisions").value(2));

        ArgumentCaptor<RejectCommissionDraftRequest> captor = ArgumentCaptor.forClass(RejectCommissionDraftRequest.class);
        verify(rejectCommissionDraftUseCase).execute(eq(105L), captor.capture(), eq(3L), eq(false));
        assertThat(captor.getValue().submissionId()).isEqualTo(42L);
        assertThat(captor.getValue().reviewNote()).isEqualTo("Increase neon edge contrast");
    }

    @Test
    @DisplayName("CMS-16 Controller UTCID02 [B]: Blank reviewNote returns 400 BAD_REQUEST (VALIDATION_FAILED)")
    void rejectCommissionDraft_blankReviewNote_returns400BadRequest() throws Exception {
        String invalidBody = """
                {
                    "submissionId": 42,
                    "reviewNote": "   "
                }
                """;

        mockMvc.perform(post(REVIEWS_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(rejectCommissionDraftUseCase);
    }

    @Test
    @DisplayName("CMS-16 Controller UTCID03 [B]: Non-positive commission id or submissionId returns 400 BAD_REQUEST (VALIDATION_FAILED)")
    void rejectCommissionDraft_nonPositiveIds_returns400BadRequest() throws Exception {
        String invalidBody = """
                {
                    "submissionId": 0,
                    "reviewNote": "Fix edges"
                }
                """;

        mockMvc.perform(post(REVIEWS_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(rejectCommissionDraftUseCase);
    }

    @Test
    @DisplayName("CMS-16 Controller UTCID04 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void rejectCommissionDraft_unauthenticated_returns401Unauthorized() throws Exception {
        String requestBody = """
                {
                    "reviewNote": "Fix edges"
                }
                """;

        mockMvc.perform(post(REVIEWS_PATH, 105L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(rejectCommissionDraftUseCase);
    }

    @Test
    @DisplayName("CMS-16 Controller UTCID05 [A]: Caller with DESIGNER or CUSTOMER role returns 403 FORBIDDEN")
    void rejectCommissionDraft_nonStaffRole_returns403Forbidden() throws Exception {
        String requestBody = """
                {
                    "reviewNote": "Fix edges"
                }
                """;

        mockMvc.perform(post(REVIEWS_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("5").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(rejectCommissionDraftUseCase);
    }

    @Test
    @DisplayName("CMS-16 Controller UTCID06 [A]: UseCase throws CommissionStaffLockException -> returns 403 FORBIDDEN")
    void rejectCommissionDraft_staffLocked_returns403Forbidden() throws Exception {
        when(rejectCommissionDraftUseCase.execute(eq(105L), any(RejectCommissionDraftRequest.class), eq(8L), eq(false)))
                .thenThrow(new CommissionStaffLockException("Commission request 105 is locked by assigned staff 3"));

        String requestBody = """
                {
                    "reviewNote": "Fix edges"
                }
                """;

        mockMvc.perform(post(REVIEWS_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("8").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMISSION_STAFF_LOCKED"));
    }

    @Test
    @DisplayName("CMS-16 Controller UTCID07 [A]: UseCase throws CommissionSubmissionNotFoundException -> returns 404 NOT_FOUND")
    void rejectCommissionDraft_submissionNotFound_returns404NotFound() throws Exception {
        when(rejectCommissionDraftUseCase.execute(eq(105L), any(RejectCommissionDraftRequest.class), eq(3L), eq(false)))
                .thenThrow(new CommissionSubmissionNotFoundException("No pending draft submission found for commission 105"));

        String requestBody = """
                {
                    "reviewNote": "Fix edges"
                }
                """;

        mockMvc.perform(post(REVIEWS_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMISSION_SUBMISSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("CMS-16 Controller UTCID08 [A]: UseCase throws InvalidCommissionStatusTransitionException -> returns 409 CONFLICT")
    void rejectCommissionDraft_invalidStatus_returns409Conflict() throws Exception {
        when(rejectCommissionDraftUseCase.execute(eq(105L), any(RejectCommissionDraftRequest.class), eq(3L), eq(false)))
                .thenThrow(new InvalidCommissionStatusTransitionException("Cannot reject draft internally for commission in status IN_PROGRESS (must be INTERNAL_REVIEW)"));

        String requestBody = """
                {
                    "reviewNote": "Fix edges"
                }
                """;

        mockMvc.perform(post(REVIEWS_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_COMMISSION_STATUS_TRANSITION"));
    }

    private PostDeliverableCardResponse createSampleDeliverableCardResponse() {
        CommissionMessageResponse card = CommissionMessageResponse.builder()
                .messageId(1201L)
                .commissionId(105L)
                .senderId(3L)
                .senderRole(CommissionMessageSenderRole.STAFF)
                .messageType(CommissionMessageType.DELIVERABLE_CARD)
                .content("Preview version 1 is ready for your review.")
                .mediaUrl("https://cdn.lumintra.shop/commissions/105/drafts/v1-neon-cat.gif")
                .metadataJson("{\"submissionId\":42,\"versionNumber\":1}")
                .createdAt(Instant.parse("2026-11-17T10:00:00Z"))
                .build();
        return PostDeliverableCardResponse.builder()
                .commissionId(105L)
                .commissionStatus(CommissionStatus.PREVIEW_DELIVERED)
                .approvedSubmissionId(42L)
                .versionNumber(1)
                .submissionStatus(CommissionSubmissionStatus.STAFF_APPROVED)
                .autoCompleteAt(Instant.parse("2026-11-24T10:00:00Z"))
                .deliverableCard(card)
                .build();
    }

    @Test
    @DisplayName("CMS-17 Controller UTCID01 [N]: Assigned STAFF posts deliverable card -> returns 201 CREATED with PostDeliverableCardResponse")
    void postDeliverableCard_assignedStaff_returns201Created() throws Exception {
        when(postDeliverableCardUseCase.execute(105L, 3L, false)).thenReturn(createSampleDeliverableCardResponse());

        mockMvc.perform(post(DELIVERABLE_CARD_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.commissionId").value(105))
                .andExpect(jsonPath("$.data.commissionStatus").value("PREVIEW_DELIVERED"))
                .andExpect(jsonPath("$.data.approvedSubmissionId").value(42))
                .andExpect(jsonPath("$.data.versionNumber").value(1))
                .andExpect(jsonPath("$.data.submissionStatus").value("STAFF_APPROVED"))
                .andExpect(jsonPath("$.data.autoCompleteAt").value("2026-11-24T10:00:00Z"))
                .andExpect(jsonPath("$.data.deliverableCard.messageId").value(1201))
                .andExpect(jsonPath("$.data.deliverableCard.senderRole").value("STAFF"))
                .andExpect(jsonPath("$.data.deliverableCard.messageType").value("DELIVERABLE_CARD"));

        verify(postDeliverableCardUseCase).execute(105L, 3L, false);
    }

    @Test
    @DisplayName("CMS-17 Controller UTCID02 [N]: MANAGER caller is passed as privileged override -> returns 201 CREATED")
    void postDeliverableCard_manager_passesPrivilegedFlag() throws Exception {
        when(postDeliverableCardUseCase.execute(105L, 9L, true)).thenReturn(createSampleDeliverableCardResponse());

        mockMvc.perform(post(DELIVERABLE_CARD_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("9").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        verify(postDeliverableCardUseCase).execute(105L, 9L, true);
    }

    @Test
    @DisplayName("CMS-17 Controller UTCID03 [B]: Non-positive id returns 400 BAD_REQUEST")
    void postDeliverableCard_nonPositiveId_returns400BadRequest() throws Exception {
        mockMvc.perform(post(DELIVERABLE_CARD_PATH, 0L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(postDeliverableCardUseCase);
    }

    @Test
    @DisplayName("CMS-17 Controller UTCID04 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void postDeliverableCard_unauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(post(DELIVERABLE_CARD_PATH, 105L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(postDeliverableCardUseCase);
    }

    @Test
    @DisplayName("CMS-17 Controller UTCID05 [A]: DESIGNER role returns 403 FORBIDDEN")
    void postDeliverableCard_designerRole_returns403Forbidden() throws Exception {
        mockMvc.perform(post(DELIVERABLE_CARD_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("5").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(postDeliverableCardUseCase);
    }

    @Test
    @DisplayName("CMS-17 Controller UTCID06 [A]: CUSTOMER role returns 403 FORBIDDEN")
    void postDeliverableCard_customerRole_returns403Forbidden() throws Exception {
        mockMvc.perform(post(DELIVERABLE_CARD_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("501").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(postDeliverableCardUseCase);
    }

    @Test
    @DisplayName("CMS-17 Controller UTCID07 [A]: Use case throws CommissionStaffLockException -> returns 403 COMMISSION_STAFF_LOCKED")
    void postDeliverableCard_lockedByAnotherStaff_returns403Forbidden() throws Exception {
        when(postDeliverableCardUseCase.execute(105L, 4L, false))
                .thenThrow(new CommissionStaffLockException("Commission request 105 is locked by assigned staff 3"));

        mockMvc.perform(post(DELIVERABLE_CARD_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMISSION_STAFF_LOCKED"));
    }

    @Test
    @DisplayName("CMS-17 Controller UTCID08 [A]: Use case throws CommissionNotFoundException -> returns 404 COMMISSION_NOT_FOUND")
    void postDeliverableCard_notFound_returns404NotFound() throws Exception {
        when(postDeliverableCardUseCase.execute(999L, 3L, false))
                .thenThrow(new CommissionNotFoundException("Commission request not found with id: 999"));

        mockMvc.perform(post(DELIVERABLE_CARD_PATH, 999L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMISSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("CMS-17 Controller UTCID09 [A]: Use case throws InvalidCommissionStatusTransitionException -> returns 409 CONFLICT")
    void postDeliverableCard_invalidStatus_returns409Conflict() throws Exception {
        when(postDeliverableCardUseCase.execute(105L, 3L, false))
                .thenThrow(new InvalidCommissionStatusTransitionException("Cannot post deliverable card for commission in status IN_PROGRESS (must be INTERNAL_REVIEW)"));

        mockMvc.perform(post(DELIVERABLE_CARD_PATH, 105L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_COMMISSION_STATUS_TRANSITION"));
    }
}
