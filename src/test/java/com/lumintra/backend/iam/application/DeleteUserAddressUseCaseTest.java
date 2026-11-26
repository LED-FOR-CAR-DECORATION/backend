package com.lumintra.backend.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAddressEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAddressRepository;
import com.lumintra.backend.shared.exception.commerce.AddressNotFoundException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class DeleteUserAddressUseCaseTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private UserAddressRepository userAddressRepository;

    private DeleteUserAddressUseCase useCase;
    private UserAccountEntity activeCustomerAccount;

    @BeforeEach
    void setUp() {
        useCase = new DeleteUserAddressUseCase(userAccountRepository, userAddressRepository);
        activeCustomerAccount =
                UserAccountEntity.createCustomerAccount("customer@lumintra.shop", "secret-hash");
        activeCustomerAccount.setAccountId(42L);
        activeCustomerAccount.setAccountType(AccountType.CUSTOMER);
        activeCustomerAccount.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID01 [N]: AC02 & AC03 deletes owned non-default address via soft-delete without promoting another address")
    void execute_ownedNonDefaultAddress_softDeletesWithoutPromotion() {
        UserAddressEntity target =
                UserAddressEntity.of(
                        11L,
                        42L,
                        "Nguyen Van B",
                        "0912345678",
                        "456 Le Loi, District 1, HCMC",
                        new BigDecimal("10.7731235"),
                        new BigDecimal("106.6987654"),
                        false);

        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(11L, 42L))
                .thenReturn(Optional.of(target));
        when(userAddressRepository.saveAndFlush(target)).thenReturn(target);

        Instant beforeDelete = Instant.now();
        useCase.execute("42", 11L);
        Instant afterDelete = Instant.now();

        assertThat(target.isDeleted()).isTrue();
        assertThat(target.isDefault()).isFalse();
        assertThat(target.getDeletedAt()).isBetween(beforeDelete, afterDelete);

        verify(userAddressRepository).saveAndFlush(target);
        verify(userAddressRepository, never()).delete(any());
        verify(userAddressRepository, never()).deleteById(any());
        verify(userAddressRepository, never()).findActiveByCustomerIdOrdered(any(), any());
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID02 [N]: AC03 & AC04 deleting owned default address flushes soft delete then promotes newest remaining active address")
    void execute_ownedDefaultAddressWithRemaining_promotesNewestActiveAddress() {
        UserAddressEntity currentDefault =
                UserAddressEntity.of(
                        10L,
                        42L,
                        "Default Address",
                        "0901234567",
                        "123 Nguyen Hue, District 1, HCMC",
                        new BigDecimal("10.7768890"),
                        new BigDecimal("106.7008060"),
                        true);
        UserAddressEntity newestRemaining =
                UserAddressEntity.of(
                        12L,
                        42L,
                        "Secondary Address",
                        "0909998887",
                        "99 Vo Van Tan, District 3, HCMC",
                        new BigDecimal("10.7795000"),
                        new BigDecimal("106.6890000"),
                        false);

        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(10L, 42L))
                .thenReturn(Optional.of(currentDefault));
        when(userAddressRepository.saveAndFlush(any(UserAddressEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userAddressRepository.findActiveByCustomerIdOrdered(eq(42L), any(Pageable.class)))
                .thenReturn(List.of(newestRemaining));

        useCase.execute("42", 10L);

        assertThat(currentDefault.isDeleted()).isTrue();
        assertThat(currentDefault.isDefault()).isFalse();
        assertThat(currentDefault.getDeletedAt()).isNotNull();
        assertThat(newestRemaining.isDefault()).isTrue();
        assertThat(newestRemaining.isDeleted()).isFalse();

        InOrder ordered = inOrder(userAddressRepository);
        ordered.verify(userAddressRepository)
                .findByAddressIdAndCustomerIdAndIsDeletedFalse(10L, 42L);
        ordered.verify(userAddressRepository).saveAndFlush(currentDefault);
        ordered.verify(userAddressRepository)
                .findActiveByCustomerIdOrdered(42L, PageRequest.of(0, 1));
        ordered.verify(userAddressRepository).saveAndFlush(newestRemaining);
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID03 [B]: AC03 & AC04 deleting last remaining default address leaves zero active addresses with no promotion write")
    void execute_lastRemainingDefaultAddress_completesWithoutPromotion() {
        UserAddressEntity lastDefault =
                UserAddressEntity.of(
                        10L,
                        42L,
                        "Only Address",
                        "0901234567",
                        "123 Nguyen Hue, District 1, HCMC",
                        new BigDecimal("10.7768890"),
                        new BigDecimal("106.7008060"),
                        true);

        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(10L, 42L))
                .thenReturn(Optional.of(lastDefault));
        when(userAddressRepository.saveAndFlush(lastDefault)).thenReturn(lastDefault);
        when(userAddressRepository.findActiveByCustomerIdOrdered(eq(42L), any(Pageable.class)))
                .thenReturn(List.of());

        useCase.execute("42", 10L);

        assertThat(lastDefault.isDeleted()).isTrue();
        assertThat(lastDefault.isDefault()).isFalse();
        verify(userAddressRepository).saveAndFlush(lastDefault);
        verify(userAddressRepository)
                .findActiveByCustomerIdOrdered(42L, PageRequest.of(0, 1));
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID04 [B]: AC02 null or non-positive addressId throws AddressNotFoundException before repository query")
    void execute_nonPositiveAddressId_throwsAddressNotFoundWithoutRepoQuery() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));

        for (Long badId : new Long[] {null, 0L, -5L}) {
            assertThatThrownBy(() -> useCase.execute("42", badId))
                    .isInstanceOf(AddressNotFoundException.class)
                    .hasMessage("Delivery address not found.");
        }

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID05 [A]: AC02 missing, soft-deleted, or foreign customer address throws AddressNotFoundException (404)")
    void execute_missingDeletedOrForeignAddress_throwsAddressNotFound() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(99L, 42L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("42", 99L))
                .isInstanceOf(AddressNotFoundException.class)
                .hasMessage("Delivery address not found.");

        verify(userAddressRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID06 [A]: AC01 blank, non-numeric, or non-positive subject rejects before DB query")
    void execute_invalidSubject_throwsInvalidCredentialsWithoutDbQuery() {
        for (String subject :
                new String[] {null, "", "   ", "abc", "0", "-1", "9223372036854775808"}) {
            assertThatThrownBy(() -> useCase.execute(subject, 10L))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        verifyNoInteractions(userAccountRepository, userAddressRepository);
    }

    @Test
    @DisplayName("IAM-25 UTCID07 [A]: AC01 missing account in DB throws UserNotFoundException (404)")
    void execute_missingAccount_throwsUserNotFound() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("42", 10L))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName("IAM-25 UTCID08 [A]: AC01 non-ACTIVE account statuses throw AccessDeniedException (403)")
    void execute_inactiveAccountStatus_throwsAccessDenied() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));

        for (AccountStatus status : AccountStatus.values()) {
            if (status == AccountStatus.ACTIVE) {
                continue;
            }
            activeCustomerAccount.setStatus(status);
            assertThatThrownBy(() -> useCase.execute("42", 10L))
                    .isInstanceOf(AccessDeniedException.class);
        }

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID09 [A]: AC01 EMPLOYEE accountType throws AccessDeniedException (403) even when ACTIVE")
    void execute_employeeAccountType_throwsAccessDenied() {
        activeCustomerAccount.setAccountType(AccountType.EMPLOYEE);
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));

        assertThatThrownBy(() -> useCase.execute("42", 10L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName("IAM-25 UTCID10 [A]: AC05 persistence failure propagates cleanly for transaction rollback")
    void execute_databaseOutage_propagatesException() {
        UserAddressEntity existing =
                UserAddressEntity.of(
                        10L,
                        42L,
                        "Nguyen Van A",
                        "0901234567",
                        "123 Nguyen Hue, District 1, HCMC",
                        new BigDecimal("10.7768890"),
                        new BigDecimal("106.7008060"),
                        true);
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(10L, 42L))
                .thenReturn(Optional.of(existing));
        when(userAddressRepository.saveAndFlush(any(UserAddressEntity.class)))
                .thenThrow(new DataAccessResourceFailureException("PostgreSQL unavailable"));

        assertThatThrownBy(() -> useCase.execute("42", 10L))
                .isInstanceOf(DataAccessException.class);
    }
}
