package com.lumintra.backend.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumintra.backend.iam.api.request.UpdateUserAddressRequest;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class UpdateUserAddressUseCaseTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private UserAddressRepository userAddressRepository;

    private UpdateUserAddressUseCase useCase;
    private UserAccountEntity activeCustomerAccount;

    @BeforeEach
    void setUp() {
        useCase =
                new UpdateUserAddressUseCase(
                        userAccountRepository, userAddressRepository, new UserAddressMapper());
        activeCustomerAccount =
                UserAccountEntity.createCustomerAccount("customer@lumintra.shop", "secret-hash");
        activeCustomerAccount.setAccountId(42L);
        activeCustomerAccount.setAccountType(AccountType.CUSTOMER);
        activeCustomerAccount.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID01 [N]: AC02-AC05 updates owned default address, rounds Goong coordinates to 7 decimals, and preserves isDefault=true")
    void execute_ownedDefaultAddress_updatesDetailsPreservingDefaultAndRoundsCoordinates() {
        Instant createdAt = Instant.parse("2026-11-12T02:00:00Z");
        Instant updatedAt = Instant.parse("2026-11-12T03:00:00Z");
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
        existing.setCreatedAt(createdAt);
        existing.setUpdatedAt(createdAt);

        var request =
                new UpdateUserAddressRequest(
                        "Nguyen Van B",
                        "+84912345678",
                        "456 Le Loi, Ben Thanh, District 1, Ho Chi Minh City",
                        new BigDecimal("10.773123456"),
                        new BigDecimal("106.698765432"));

        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findByAddressIdAndCustomerId(10L, 42L))
                .thenReturn(Optional.of(existing));
        when(userAddressRepository.saveAndFlush(existing))
                .thenAnswer(
                        invocation -> {
                            UserAddressEntity arg = invocation.getArgument(0);
                            arg.setUpdatedAt(updatedAt);
                            return arg;
                        });

        UserAddressResponse response = useCase.execute("42", 10L, request);

        verify(userAddressRepository).saveAndFlush(existing);
        assertThat(existing.getCustomerId()).isEqualTo(42L);
        assertThat(existing.isDefault()).isTrue();
        assertThat(existing.getLatitude()).isEqualByComparingTo("10.7731235");
        assertThat(existing.getLatitude().scale()).isEqualTo(7);
        assertThat(existing.getLongitude()).isEqualByComparingTo("106.6987654");
        assertThat(existing.getLongitude().scale()).isEqualTo(7);

        assertThat(response.addressId()).isEqualTo(10L);
        assertThat(response.recipientName()).isEqualTo("Nguyen Van B");
        assertThat(response.recipientPhone()).isEqualTo("+84912345678");
        assertThat(response.fullAddress())
                .isEqualTo("456 Le Loi, Ben Thanh, District 1, Ho Chi Minh City");
        assertThat(response.latitude()).isEqualByComparingTo("10.7731235");
        assertThat(response.longitude()).isEqualByComparingTo("106.6987654");
        assertThat(response.isDefault()).isTrue();
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID02 [N]: AC04 updates owned non-default address and preserves isDefault=false")
    void execute_ownedNonDefaultAddress_preservesFalseDefault() {
        UserAddressEntity existing =
                UserAddressEntity.of(
                        11L,
                        42L,
                        "Old Name",
                        "0901234567",
                        "Old Street, District 1, HCMC",
                        new BigDecimal("10.7700000"),
                        new BigDecimal("106.7000000"),
                        false);

        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findByAddressIdAndCustomerId(11L, 42L))
                .thenReturn(Optional.of(existing));
        when(userAddressRepository.saveAndFlush(existing)).thenReturn(existing);

        UserAddressResponse response = useCase.execute("42", 11L, sampleRequest());

        assertThat(response.addressId()).isEqualTo(11L);
        assertThat(response.recipientName()).isEqualTo("Nguyen Van B");
        assertThat(response.isDefault()).isFalse();
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID03 [A]: AC02 missing address or address owned by another customer throws AddressNotFoundException (404)")
    void execute_missingOrForeignAddress_throwsAddressNotFound() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.findByAddressIdAndCustomerId(99L, 42L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("42", 99L, sampleRequest()))
                .isInstanceOf(AddressNotFoundException.class)
                .hasMessage("Delivery address not found.");
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID04 [B]: AC02 null or non-positive addressId throws AddressNotFoundException before repository query")
    void execute_nonPositiveAddressId_throwsAddressNotFoundWithoutRepoQuery() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));

        for (Long badId : new Long[] {null, 0L, -5L}) {
            assertThatThrownBy(() -> useCase.execute("42", badId, sampleRequest()))
                    .isInstanceOf(AddressNotFoundException.class);
        }

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID05 [A]: AC01 blank, non-numeric or non-positive subject rejects before DB lookup")
    void execute_invalidSubject_throwsInvalidCredentialsWithoutDbQuery() {
        var request = sampleRequest();
        for (String subject :
                new String[] {null, "", "   ", "abc", "0", "-1", "9223372036854775808"}) {
            assertThatThrownBy(() -> useCase.execute(subject, 10L, request))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        verifyNoInteractions(userAccountRepository, userAddressRepository);
    }

    @Test
    @DisplayName("IAM-24 UTCID06 [A]: AC01 missing account in DB throws UserNotFoundException (404)")
    void execute_missingAccount_throwsUserNotFound() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("42", 10L, sampleRequest()))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName("IAM-24 UTCID07 [A]: AC01 non-ACTIVE account statuses throw AccessDeniedException (403)")
    void execute_inactiveAccountStatus_throwsAccessDenied() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));

        for (AccountStatus status : AccountStatus.values()) {
            if (status == AccountStatus.ACTIVE) {
                continue;
            }
            activeCustomerAccount.setStatus(status);
            assertThatThrownBy(() -> useCase.execute("42", 10L, sampleRequest()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID08 [A]: AC01 EMPLOYEE accountType throws AccessDeniedException (403) even when ACTIVE")
    void execute_employeeAccountType_throwsAccessDenied() {
        activeCustomerAccount.setAccountType(AccountType.EMPLOYEE);
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));

        assertThatThrownBy(() -> useCase.execute("42", 10L, sampleRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName("IAM-24 UTCID09 [A]: AC05 persistence failure propagates cleanly without swallowing")
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
        when(userAddressRepository.findByAddressIdAndCustomerId(10L, 42L))
                .thenReturn(Optional.of(existing));
        when(userAddressRepository.saveAndFlush(any(UserAddressEntity.class)))
                .thenThrow(new DataAccessResourceFailureException("PostgreSQL unavailable"));

        assertThatThrownBy(() -> useCase.execute("42", 10L, sampleRequest()))
                .isInstanceOf(DataAccessException.class);
    }

    private UpdateUserAddressRequest sampleRequest() {
        return new UpdateUserAddressRequest(
                "Nguyen Van B",
                "0912345678",
                "456 Le Loi, Ben Thanh, District 1, Ho Chi Minh City",
                new BigDecimal("10.7731235"),
                new BigDecimal("106.6987654"));
    }
}
