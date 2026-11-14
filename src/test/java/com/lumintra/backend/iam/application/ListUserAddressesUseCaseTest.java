package com.lumintra.backend.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ListUserAddressesUseCaseTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private UserAddressRepository userAddressRepository;

    private ListUserAddressesUseCase useCase;
    private UserAccountEntity activeCustomerAccount;

    @BeforeEach
    void setUp() {
        useCase =
                new ListUserAddressesUseCase(
                        userAccountRepository, userAddressRepository, new UserAddressMapper());
        activeCustomerAccount =
                UserAccountEntity.createCustomerAccount("customer@lumintra.shop", "secret-hash");
        activeCustomerAccount.setAccountId(42L);
        activeCustomerAccount.setAccountType(AccountType.CUSTOMER);
        activeCustomerAccount.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("IAM-22 UTCID01 [N]: AC01-AC04 returns mapped addresses for active CUSTOMER account preserving repository order")
    void execute_activeCustomerWithAddresses_returnsMappedList() {
        Instant createdAt = Instant.parse("2026-11-10T02:15:00Z");
        Instant updatedAt = Instant.parse("2026-11-10T03:00:00Z");

        UserAddressEntity defaultAddress =
                UserAddressEntity.of(
                        10L,
                        42L,
                        "Nguyen Van A",
                        "0901234567",
                        "123 Nguyen Hue, Ben Nghe, District 1, HCMC",
                        new BigDecimal("10.7768890"),
                        new BigDecimal("106.7008060"),
                        true);
        defaultAddress.setCreatedAt(createdAt);
        defaultAddress.setUpdatedAt(updatedAt);

        UserAddressEntity secondaryAddress =
                UserAddressEntity.of(
                        11L,
                        42L,
                        "Nguyen Van B",
                        "0907654321",
                        "45 Le Loi, Ben Thanh, District 1, HCMC",
                        new BigDecimal("10.7720000"),
                        new BigDecimal("106.6980000"),
                        false);
        secondaryAddress.setCreatedAt(createdAt);
        secondaryAddress.setUpdatedAt(null);

        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findAllByCustomerIdOrdered(42L))
                .thenReturn(List.of(defaultAddress, secondaryAddress));

        List<UserAddressResponse> result = useCase.execute("42");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).addressId()).isEqualTo(10L);
        assertThat(result.get(0).isDefault()).isTrue();
        assertThat(result.get(0).latitude()).isEqualByComparingTo("10.7768890");
        assertThat(result.get(0).longitude()).isEqualByComparingTo("106.7008060");
        assertThat(result.get(1).addressId()).isEqualTo(11L);
        assertThat(result.get(1).isDefault()).isFalse();
        assertThat(result.get(1).updatedAt()).isNull();

        verify(userAccountRepository).findById(42L);
        verify(userAddressRepository).findAllByCustomerIdOrdered(42L);
    }

    @Test
    @DisplayName("IAM-22 UTCID02 [B]: AC04 returns empty list when active customer has no saved addresses")
    void execute_activeCustomerWithNoAddresses_returnsEmptyList() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findAllByCustomerIdOrdered(42L)).thenReturn(List.of());

        List<UserAddressResponse> result = useCase.execute("42");

        assertThat(result).isEmpty();
        verify(userAddressRepository).findAllByCustomerIdOrdered(42L);
    }

    @Test
    @DisplayName("IAM-22 UTCID03 [A]: AC01 invalid, blank, non-numeric or non-positive subject rejects before DB query")
    void execute_invalidSubject_throwsInvalidCredentialsWithoutDbQuery() {
        for (String subject :
                new String[] {null, "", "   ", "abc", "0", "-1", "9223372036854775808"}) {
            assertThatThrownBy(() -> useCase.execute(subject))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        verifyNoInteractions(userAccountRepository, userAddressRepository);
    }

    @Test
    @DisplayName("IAM-22 UTCID04 [A]: AC01 missing account in DB throws UserNotFoundException (404) without querying addresses")
    void execute_missingAccount_throwsUserNotFound() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("42"))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName("IAM-22 UTCID05 [A]: AC01 non-ACTIVE account statuses (PENDING_VERIFICATION, SUSPENDED, DELETED) throw AccessDeniedException (403)")
    void execute_inactiveAccountStatus_throwsAccessDenied() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));

        for (AccountStatus status : AccountStatus.values()) {
            if (status == AccountStatus.ACTIVE) {
                continue;
            }
            activeCustomerAccount.setStatus(status);
            assertThatThrownBy(() -> useCase.execute("42"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName("IAM-22 UTCID06 [A]: AC01 non-CUSTOMER accountType (EMPLOYEE) throws AccessDeniedException (403) even when ACTIVE")
    void execute_nonCustomerAccountType_throwsAccessDenied() {
        activeCustomerAccount.setAccountType(AccountType.EMPLOYEE);
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));

        assertThatThrownBy(() -> useCase.execute("42"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName("IAM-22 UTCID07 [A]: AC05 database failure propagates cleanly instead of returning empty list")
    void execute_databaseOutage_propagatesException() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findAllByCustomerIdOrdered(42L))
                .thenThrow(new DataAccessResourceFailureException("PostgreSQL unavailable"));

        assertThatThrownBy(() -> useCase.execute("42"))
                .isInstanceOf(DataAccessException.class);
    }
}
