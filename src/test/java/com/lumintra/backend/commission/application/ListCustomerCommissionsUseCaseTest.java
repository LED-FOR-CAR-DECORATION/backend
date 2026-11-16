package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.CustomerCommissionFilterRequest;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListCustomerCommissionsUseCaseTest {

    @Mock
    private CommissionRequestRepository commissionRepository;

    private ListCustomerCommissionsUseCase useCase;

    @BeforeEach
    void setUp() {
        CommissionRequestMapper mapper = new CommissionRequestMapper();
        useCase = new ListCustomerCommissionsUseCase(commissionRepository, mapper);
    }

    @Test
    @DisplayName("CMS-04 UseCase UTCID01 [N]: Lists customer commissions without status filter sorted by createdAt DESC")
    void execute_withoutStatusFilter_queriesAllCustomerCommissionsSortedDesc() {
        CommissionRequestEntity entity = CommissionRequestEntity.builder()
                .commissionId(501L)
                .customerId(10L)
                .title("Retro Sunset LED")
                .tierCode("BASIC")
                .targetMatrixDimensions("32x32")
                .initialPrice(new BigDecimal("79000.00"))
                .extraRevisionFee(BigDecimal.ZERO)
                .totalPrice(new BigDecimal("79000.00"))
                .revisionsUsed(0)
                .maxRevisions(1)
                .status(CommissionStatus.PENDING_REVIEW)
                .createdAt(Instant.parse("2026-11-16T08:00:00Z"))
                .updatedAt(Instant.parse("2026-11-16T08:00:00Z"))
                .build();

        CustomerCommissionFilterRequest filter = new CustomerCommissionFilterRequest(null, "DESC", 0, 20);
        Page<CommissionRequestEntity> page = new PageImpl<>(List.of(entity), filter.toPageable(), 1);

        when(commissionRepository.findAllByCustomerId(eq(10L), any(Pageable.class))).thenReturn(page);

        CustomerCommissionListResponse response = useCase.execute(10L, filter);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).commissionId()).isEqualTo(501L);
        assertThat(response.items().get(0).status()).isEqualTo(CommissionStatus.PENDING_REVIEW);
        assertThat(response.meta().getTotalElements()).isEqualTo(1);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(commissionRepository).findAllByCustomerId(eq(10L), pageableCaptor.capture());
        Sort.Order createdAtOrder = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
        assertThat(createdAtOrder).isNotNull();
        assertThat(createdAtOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("CMS-04 UseCase UTCID02 [N]: Lists customer commissions filtered by status and sorted ASC")
    void execute_withStatusFilterAndAscSort_queriesFilteredCustomerCommissions() {
        CommissionRequestEntity entity = CommissionRequestEntity.builder()
                .commissionId(502L)
                .customerId(10L)
                .title("Animated Drift Car")
                .tierCode("ADVANCED")
                .targetMatrixDimensions("32x64")
                .initialPrice(new BigDecimal("180000.00"))
                .extraRevisionFee(BigDecimal.ZERO)
                .totalPrice(new BigDecimal("180000.00"))
                .revisionsUsed(1)
                .maxRevisions(3)
                .status(CommissionStatus.PENDING_PAYMENT)
                .activePaymentCode("LMT-CMS-502")
                .createdAt(Instant.parse("2026-11-16T07:00:00Z"))
                .build();

        CustomerCommissionFilterRequest filter = new CustomerCommissionFilterRequest(
                CommissionStatus.PENDING_PAYMENT, "ASC", 0, 10
        );
        Page<CommissionRequestEntity> page = new PageImpl<>(List.of(entity), filter.toPageable(), 1);

        when(commissionRepository.findAllByCustomerIdAndStatus(
                eq(10L), eq(CommissionStatus.PENDING_PAYMENT), any(Pageable.class)
        )).thenReturn(page);

        CustomerCommissionListResponse response = useCase.execute(10L, filter);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).activePaymentCode()).isEqualTo("LMT-CMS-502");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(commissionRepository).findAllByCustomerIdAndStatus(
                eq(10L), eq(CommissionStatus.PENDING_PAYMENT), pageableCaptor.capture()
        );
        Sort.Order createdAtOrder = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
        assertThat(createdAtOrder).isNotNull();
        assertThat(createdAtOrder.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("CMS-04 UseCase UTCID03 [B]: Null filter defaults to page=0, size=20, sortDirection=DESC")
    void execute_nullFilter_appliesDefaults() {
        CustomerCommissionFilterRequest defaultFilter = new CustomerCommissionFilterRequest();
        Page<CommissionRequestEntity> emptyPage = new PageImpl<>(List.of(), defaultFilter.toPageable(), 0);

        when(commissionRepository.findAllByCustomerId(eq(10L), any(Pageable.class))).thenReturn(emptyPage);

        CustomerCommissionListResponse response = useCase.execute(10L, null);

        assertThat(response.items()).isEmpty();
        assertThat(response.meta().getPage()).isEqualTo(0);
        assertThat(response.meta().getSize()).isEqualTo(20);
        assertThat(response.meta().getTotalElements()).isZero();
    }

    @Test
    @DisplayName("CMS-04 UseCase UTCID04 [A]: Null or non-positive customerId throws InsufficientAuthenticationException")
    void execute_invalidCustomerId_throwsException() {
        assertThatThrownBy(() -> useCase.execute(null, new CustomerCommissionFilterRequest()))
                .isInstanceOf(InsufficientAuthenticationException.class);
        assertThatThrownBy(() -> useCase.execute(0L, new CustomerCommissionFilterRequest()))
                .isInstanceOf(InsufficientAuthenticationException.class);

        verifyNoInteractions(commissionRepository);
    }
}
