package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.ManagementCommissionDetailResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionAttachmentEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionAttachmentRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetManagementCommissionDetailUseCaseTest {

    @Mock
    private CommissionRequestRepository commissionRequestRepository;

    @Mock
    private CommissionAttachmentRepository commissionAttachmentRepository;

    @Mock
    private CommissionSubmissionRepository commissionSubmissionRepository;

    @Mock
    private CustomerLookupService customerLookupService;

    @Mock
    private EmployeeLookupService employeeLookupService;

    private CommissionRequestMapper commissionRequestMapper;
    private GetManagementCommissionDetailUseCase useCase;
    private final com.lumintra.backend.commission.RevisionRequestFakes revisions =
            new com.lumintra.backend.commission.RevisionRequestFakes();

    @BeforeEach
    void setUp() {
        commissionRequestMapper = new CommissionRequestMapper();
        useCase = new GetManagementCommissionDetailUseCase(
                commissionRequestRepository,
                commissionAttachmentRepository,
                commissionSubmissionRepository,
                revisions.repository,
                commissionRequestMapper,
                customerLookupService,
                employeeLookupService
        );
    }

    private CommissionRequestEntity createSampleEntity(Long id, Long customerId, Long staffId, Long designerId) {
        CommissionRequestEntity entity = CommissionRequestEntity.createPendingReview(
                customerId,
                "Cyberpunk Neon Cat Animation",
                "Animated neon cat blinking",
                "ADVANCED",
                "32x64",
                "{\"tierCode\":\"ADVANCED\",\"basePrice\":250000.00}",
                new BigDecimal("250000.00"),
                2,
                staffId
        );
        entity.setCommissionId(id);
        entity.setAssignedDesignerId(designerId);
        entity.setStatus(CommissionStatus.INTERNAL_REVIEW);
        entity.setQuoteExpiresAt(Instant.parse("2026-11-19T10:00:00Z"));
        entity.setSlaDueAt(Instant.parse("2026-11-18T10:00:00Z"));
        entity.setAutoCompleteAt(Instant.parse("2026-11-22T10:00:00Z"));
        return entity;
    }

    @Test
    @DisplayName("CMS-07 UseCase UTCID01 [N]: Privileged STAFF retrieves full commission details with attachments and full submission history")
    void execute_asStaff_returnsFullManagementDetails() {
        CommissionRequestEntity entity = createSampleEntity(105L, 12L, 3L, 5L);
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(entity));

        CommissionAttachmentEntity attachment = CommissionAttachmentEntity.create(
                105L, "https://cdn.lumintra.shop/commissions/ref-cat.png", "Cat ears", 0
        );
        when(commissionAttachmentRepository.findAllByCommissionIdOrderByDisplayOrderAscAttachmentIdAsc(105L))
                .thenReturn(List.of(attachment));

        CommissionSubmissionEntity draft1 = CommissionSubmissionEntity.createDraft(
                105L, 5L, 1, "https://cdn.lumintra.shop/commissions/draft-v1.gif", "Initial draft"
        );
        draft1.setStatus(CommissionSubmissionStatus.STAFF_REJECTED);
        draft1.setReviewerStaffId(3L);
        draft1.setStaffReviewNote("Blinking too fast");
        draft1.setReviewedAt(Instant.parse("2026-11-17T08:15:00Z"));

        CommissionSubmissionEntity draft2 = CommissionSubmissionEntity.createDraft(
                105L, 5L, 2, "https://cdn.lumintra.shop/commissions/draft-v2.gif", "Adjusted speed"
        );
        when(commissionSubmissionRepository.findAllByCommissionIdOrderByVersionNumberAscSubmissionIdAsc(105L))
                .thenReturn(List.of(draft1, draft2));

        when(customerLookupService.findProfileByCustomerId(12L))
                .thenReturn(Optional.of(new CustomerProfileSnapshot(12L, "Nguyen Van A", "0901234567", "nguyenvana@gmail.com")));

        when(employeeLookupService.findProfilesByEmployeeIds(eq(Set.of(3L, 5L))))
                .thenReturn(Map.of(
                        3L, new EmployeeProfileSnapshot(3L, "Tran Thi Staff", "staff@lumintra.com"),
                        5L, new EmployeeProfileSnapshot(5L, "Le Van Designer", "designer@lumintra.com")
                ));

        ManagementCommissionDetailResponse response = useCase.execute(105L, 3L, true);

        assertThat(response).isNotNull();
        assertThat(response.commissionId()).isEqualTo(105L);
        assertThat(response.customerId()).isEqualTo(12L);
        assertThat(response.customerName()).isEqualTo("Nguyen Van A");
        assertThat(response.customerEmail()).isEqualTo("nguyenvana@gmail.com");
        assertThat(response.customerPhone()).isEqualTo("0901234567");
        assertThat(response.title()).isEqualTo("Cyberpunk Neon Cat Animation");
        assertThat(response.tierCode()).isEqualTo("ADVANCED");
        assertThat(response.status()).isEqualTo(CommissionStatus.INTERNAL_REVIEW);
        assertThat(response.assignedStaffId()).isEqualTo(3L);
        assertThat(response.assignedStaffName()).isEqualTo("Tran Thi Staff");
        assertThat(response.assignedDesignerId()).isEqualTo(5L);
        assertThat(response.assignedDesignerName()).isEqualTo("Le Van Designer");
        assertThat(response.chatActive()).isTrue();

        assertThat(response.attachments()).hasSize(1);
        assertThat(response.attachments().get(0).imageUrl()).isEqualTo("https://cdn.lumintra.shop/commissions/ref-cat.png");

        assertThat(response.submissions()).hasSize(2);
        var sub1 = response.submissions().get(0);
        assertThat(sub1.versionNumber()).isEqualTo(1);
        assertThat(sub1.status()).isEqualTo(CommissionSubmissionStatus.STAFF_REJECTED);
        assertThat(sub1.staffReviewNote()).isEqualTo("Blinking too fast");
        assertThat(sub1.reviewerStaffName()).isEqualTo("Tran Thi Staff");

        var sub2 = response.submissions().get(1);
        assertThat(sub2.versionNumber()).isEqualTo(2);
        assertThat(sub2.status()).isEqualTo(CommissionSubmissionStatus.PENDING_STAFF_REVIEW);
    }

    @Test
    @DisplayName("CMS-07 UseCase UTCID02 [N]: Assigned DESIGNER retrieves full details of their own assigned ticket")
    void execute_asAssignedDesigner_returnsFullDetails() {
        CommissionRequestEntity entity = createSampleEntity(105L, 12L, 3L, 5L);
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(entity));
        when(commissionAttachmentRepository.findAllByCommissionIdOrderByDisplayOrderAscAttachmentIdAsc(105L)).thenReturn(List.of());
        when(commissionSubmissionRepository.findAllByCommissionIdOrderByVersionNumberAscSubmissionIdAsc(105L)).thenReturn(List.of());

        ManagementCommissionDetailResponse response = useCase.execute(105L, 5L, false);

        assertThat(response).isNotNull();
        assertThat(response.commissionId()).isEqualTo(105L);
        assertThat(response.assignedDesignerId()).isEqualTo(5L);
        assertThat(response.revisionRequests()).isEmpty();
    }

    @Test
    @DisplayName("Assigned DESIGNER sees the confirmed revision brief; superseded briefs are hidden")
    void execute_asAssignedDesigner_returnsRevisionBriefs() {
        CommissionRequestEntity entity = createSampleEntity(105L, 12L, 3L, 5L);
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(entity));
        when(commissionAttachmentRepository.findAllByCommissionIdOrderByDisplayOrderAscAttachmentIdAsc(105L)).thenReturn(List.of());
        when(commissionSubmissionRepository.findAllByCommissionIdOrderByVersionNumberAscSubmissionIdAsc(105L)).thenReturn(List.of());
        var stale = revisions.repository.save(
                com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRevisionRequestEntity
                        .open(105L, 401L, 1, "Old idea", true, java.math.BigDecimal.ZERO));
        stale.supersede();
        revisions.repository.save(
                        com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRevisionRequestEntity
                                .open(105L, 402L, 1, "Brighter outline", true, java.math.BigDecimal.ZERO))
                .confirmFree(1, java.time.Instant.now());

        ManagementCommissionDetailResponse response = useCase.execute(105L, 5L, false);

        assertThat(response.revisionRequests()).singleElement().satisfies(item -> {
            assertThat(item.description()).isEqualTo("Brighter outline");
            assertThat(item.messageId()).isEqualTo(402L);
            assertThat(item.status()).isEqualTo(
                    com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus.CONFIRMED);
        });
    }

    @Test
    @DisplayName("CMS-07 UseCase UTCID03 [A]: Unassigned DESIGNER is denied access to commission ticket (AccessDeniedException)")
    void execute_asUnassignedDesigner_throwsAccessDeniedException() {
        CommissionRequestEntity entity = createSampleEntity(105L, 12L, 3L, 5L);
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> useCase.execute(105L, 99L, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the designer assigned to this commission ticket");

        verifyNoInteractions(commissionAttachmentRepository);
        verifyNoInteractions(commissionSubmissionRepository);
    }

    @Test
    @DisplayName("CMS-07 UseCase UTCID04 [A]: DESIGNER is denied when no designer is assigned yet (AccessDeniedException)")
    void execute_asDesignerWhenUnassignedTicket_throwsAccessDeniedException() {
        CommissionRequestEntity entity = createSampleEntity(105L, 12L, 3L, null);
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> useCase.execute(105L, 5L, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the designer assigned to this commission ticket");
    }

    @Test
    @DisplayName("CMS-07 UseCase UTCID05 [A]: Commission not found throws CommissionNotFoundException")
    void execute_commissionNotFound_throwsCommissionNotFoundException() {
        when(commissionRequestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(999L, 1L, true))
                .isInstanceOf(CommissionNotFoundException.class)
                .hasMessageContaining("Commission request not found with id: 999");
    }

    @Test
    @DisplayName("CMS-07 UseCase UTCID06 [B]: Null or non-positive commissionId throws CommissionNotFoundException")
    void execute_nullOrNonPositiveId_throwsCommissionNotFoundException() {
        assertThatThrownBy(() -> useCase.execute(null, 1L, true))
                .isInstanceOf(CommissionNotFoundException.class);

        assertThatThrownBy(() -> useCase.execute(0L, 1L, true))
                .isInstanceOf(CommissionNotFoundException.class);
    }

    @Test
    @DisplayName("CMS-07 UseCase UTCID07 [N]: Exposes activePaymentCode when payment is pending, null otherwise")
    void execute_activePaymentCode_onlyWhenPaymentPending() {
        CommissionRequestEntity entity = createSampleEntity(105L, 12L, 3L, 5L);
        entity.setStatus(CommissionStatus.PENDING_PAYMENT);
        entity.setActivePaymentCode("LMT-CMS-105");

        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(entity));
        when(commissionAttachmentRepository.findAllByCommissionIdOrderByDisplayOrderAscAttachmentIdAsc(105L)).thenReturn(List.of());
        when(commissionSubmissionRepository.findAllByCommissionIdOrderByVersionNumberAscSubmissionIdAsc(105L)).thenReturn(List.of());

        ManagementCommissionDetailResponse response = useCase.execute(105L, 3L, true);
        assertThat(response.activePaymentCode()).isEqualTo("LMT-CMS-105");

        entity.setStatus(CommissionStatus.PAID);
        ManagementCommissionDetailResponse paidResponse = useCase.execute(105L, 3L, true);
        assertThat(paidResponse.activePaymentCode()).isNull();
    }
}
