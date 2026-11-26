package com.lumintra.backend.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumintra.backend.iam.api.response.UserAddressResponse;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAddressEntity;
import com.lumintra.backend.iam.infrastructure.persistence.mapper.UserAddressMapper;
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
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class SetDefaultUserAddressUseCaseTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private UserAddressRepository userAddressRepository;

    private SetDefaultUserAddressUseCase useCase;
    private UserAccountEntity activeCustomerAccount;

    @BeforeEach
    void setUp() {
        useCase =
                new SetDefaultUserAddressUseCase(
                        userAccountRepository, userAddressRepository, new UserAddressMapper());
        activeCustomerAccount =
                UserAccountEntity.createCustomerAccount("customer@lumintra.shop", "secret-hash");
        activeCustomerAccount.setAccountId(42L);
        activeCustomerAccount.setAccountType(AccountType.CUSTOMER);
        activeCustomerAccount.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName(
            "IAM-26 UTCID01 [N]: AC01-AC04 clears prior defaults, marks owned non-default address default, saves cleanly and maps response")
    void execute_nonDefaultOwnedAddress_switchesDefaultAndReturnsResponse() {
        Instant createdAt = Instant.parse("2026-11-10T02:00:00Z");
        Instant updatedAt = Instant.parse("2026-11-12T03:30:00Z");
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
        target.setCreatedAt(createdAt);

        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(11L, 42L))
                .thenReturn(Optional.of(target));
        when(userAddressRepository.clearDefaultByCustomerId(42L)).thenReturn(1);
        when(userAddressRepository.saveAndFlush(target))
                .thenAnswer(
                        invocation -> {
                            UserAddressEntity arg = invocation.getArgument(0);
                            UserAddressEntity saved =
                                    UserAddressEntity.of(
                                            arg.getAddressId(),
                                            arg.getCustomerId(),
                                            arg.getRecipientName(),
                                            arg.getRecipientPhone(),
                                            arg.getFullAddress(),
                                            arg.getLatitude(),
                                            arg.getLongitude(),
                                            arg.isDefault());
                            saved.setCreatedAt(arg.getCreatedAt());
                            saved.setUpdatedAt(updatedAt);
                            return saved;
                        });

        UserAddressResponse response = useCase.execute("42", 11L);

        assertThat(response.addressId()).isEqualTo(11L);
        assertThat(response.recipientName()).isEqualTo("Nguyen Van B");
        assertThat(response.isDefault()).isTrue();
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);

        InOrder ordered = inOrder(userAddressRepository);
        ordered.verify(userAddressRepository)
                .findByAddressIdAndCustomerIdAndIsDeletedFalse(11L, 42L);
        ordered.verify(userAddressRepository).clearDefaultByCustomerId(42L);
        ordered.verify(userAddressRepository).saveAndFlush(target);
        assertThat(target.isDefault()).isTrue();
    }

    @Test
    @DisplayName(
            "IAM-26 UTCID02 [N]: AC03 & AC04 calling on already-default address is idempotent without redundant clear or save")
    void execute_alreadyDefaultOwnedAddress_isIdempotent() {
        Instant createdAt = Instant.parse("2026-11-10T02:00:00Z");
        Instant updatedAt = Instant.parse("2026-11-11T05:00:00Z");
        UserAddressEntity alreadyDefault =
                UserAddressEntity.of(
                        10L,
                        42L,
                        "Nguyen Van A",
                        "0901234567",
                        "123 Nguyen Hue, District 1, HCMC",
                        new BigDecimal("10.7768890"),
                        new BigDecimal("106.7008060"),
                        true);
        alreadyDefault.setCreatedAt(createdAt);
        alreadyDefault.setUpdatedAt(updatedAt);

        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(10L, 42L))
                .thenReturn(Optional.of(alreadyDefault));

        UserAddressResponse response = useCase.execute("42", 10L);

        assertThat(response.addressId()).isEqualTo(10L);
        assertThat(response.isDefault()).isTrue();
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);

        verify(userAddressRepository, never()).clearDefaultByCustomerId(any());
        verify(userAddressRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName(
            "IAM-26 UTCID03 [B]: AC02 null or non-positive addressId throws AddressNotFoundException before repository query")
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
            "IAM-26 UTCID04 [A]: AC02 missing, soft-deleted, or foreign customer address throws AddressNotFoundException without clearing defaults")
    void execute_missingDeletedOrForeignAddress_throwsAddressNotFoundWithoutClearingDefaults() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(99L, 42L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("42", 99L))
                .isInstanceOf(AddressNotFoundException.class)
                .hasMessage("Delivery address not found.");

        verify(userAddressRepository, never()).clearDefaultByCustomerId(any());
        verify(userAddressRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName(
            "IAM-26 UTCID05 [A]: AC01 blank, non-numeric, or non-positive subject rejects before DB query")
    void execute_invalidSubject_throwsInvalidCredentialsWithoutDbQuery() {
        for (String subject :
                new String[] {null, "", "   ", "abc", "0", "-1", "9223372036854775808"}) {
            assertThatThrownBy(() -> useCase.execute(subject, 10L))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        verifyNoInteractions(userAccountRepository, userAddressRepository);
    }

    @Test
    @DisplayName("IAM-26 UTCID06 [A]: AC01 missing account in DB throws UserNotFoundException (404)")
    void execute_missingAccount_throwsUserNotFound() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("42", 10L))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName("IAM-26 UTCID07 [A]: AC01 non-ACTIVE account statuses throw AccessDeniedException (403)")
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
            "IAM-26 UTCID08 [A]: AC01 EMPLOYEE accountType throws AccessDeniedException (403) even when ACTIVE")
    void execute_employeeAccountType_throwsAccessDenied() {
        activeCustomerAccount.setAccountType(AccountType.EMPLOYEE);
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));

        assertThatThrownBy(() -> useCase.execute("42", 10L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName("IAM-26 UTCID09 [A]: AC04 persistence failure on saveAndFlush propagates DataAccessException for rollback")
    void execute_databaseOutage_propagatesException() {
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
        when(userAddressRepository.clearDefaultByCustomerId(42L)).thenReturn(1);
        when(userAddressRepository.saveAndFlush(any(UserAddressEntity.class)))
                .thenThrow(new DataAccessResourceFailureException("PostgreSQL unavailable"));

        assertThatThrownBy(() -> useCase.execute("42", 11L))
                .isInstanceOf(DataAccessException.class);
    }
}
