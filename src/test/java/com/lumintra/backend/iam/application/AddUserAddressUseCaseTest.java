package com.lumintra.backend.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumintra.backend.iam.api.request.AddUserAddressRequest;
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
import org.mockito.ArgumentCaptor;
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
class AddUserAddressUseCaseTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private UserAddressRepository userAddressRepository;

    private AddUserAddressUseCase useCase;
    private UserAccountEntity activeCustomerAccount;

    @BeforeEach
    void setUp() {
        useCase =
                new AddUserAddressUseCase(
                        userAccountRepository, userAddressRepository, new UserAddressMapper());
        activeCustomerAccount =
                UserAccountEntity.createCustomerAccount("customer@lumintra.shop", "secret-hash");
        activeCustomerAccount.setAccountId(42L);
        activeCustomerAccount.setAccountType(AccountType.CUSTOMER);
        activeCustomerAccount.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID01 [B]: AC03 & AC04 first saved address forces isDefault=true and rounds Goong Map coordinates to 7 decimals")
    void execute_firstAddressForCustomer_forcesDefaultAndRoundsCoordinates() {
        var request =
                new AddUserAddressRequest(
                        "Nguyen Van A",
                        "0901234567",
                        "123 Nguyen Hue, Ben Nghe, District 1, Ho Chi Minh City",
                        new BigDecimal("10.7768891234"),
                        new BigDecimal("106.7008065678"),
                        false);

        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.existsByCustomerIdAndIsDeletedFalse(42L)).thenReturn(false);
        when(userAddressRepository.saveAndFlush(any(UserAddressEntity.class)))
                .thenAnswer(
                        invocation -> {
                            UserAddressEntity arg = invocation.getArgument(0);
                            arg.setAddressId(10L);
                            Instant now = Instant.parse("2026-11-12T02:00:00Z");
                            arg.setCreatedAt(now);
                            arg.setUpdatedAt(now);
                            return arg;
                        });

        UserAddressResponse response = useCase.execute("42", request);

        ArgumentCaptor<UserAddressEntity> captor = ArgumentCaptor.forClass(UserAddressEntity.class);
        verify(userAddressRepository).saveAndFlush(captor.capture());
        verify(userAddressRepository, never()).clearDefaultByCustomerId(any());

        UserAddressEntity saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(42L);
        assertThat(saved.isDefault()).isTrue();
        assertThat(saved.getLatitude()).isEqualByComparingTo("10.7768891");
        assertThat(saved.getLatitude().scale()).isEqualTo(7);
        assertThat(saved.getLongitude()).isEqualByComparingTo("106.7008066");
        assertThat(saved.getLongitude().scale()).isEqualTo(7);

        assertThat(response.addressId()).isEqualTo(10L);
        assertThat(response.isDefault()).isTrue();
        assertThat(response.latitude()).isEqualByComparingTo("10.7768891");
        assertThat(response.longitude()).isEqualByComparingTo("106.7008066");
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID02 [N]: AC04 explicit isDefault=true clears previous default before saving new default address")
    void execute_existingAddressesWithDefaultTrue_clearsOldDefaultBeforeSave() {
        var request =
                new AddUserAddressRequest(
                        "Nguyen Van B",
                        "+84907654321",
                        "45 Le Loi, District 1, HCMC",
                        new BigDecimal("10.7731"),
                        new BigDecimal("106.7012"),
                        true);

        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.existsByCustomerIdAndIsDeletedFalse(42L)).thenReturn(true);
        when(userAddressRepository.clearDefaultByCustomerId(42L)).thenReturn(1);
        when(userAddressRepository.saveAndFlush(any(UserAddressEntity.class)))
                .thenAnswer(
                        invocation -> {
                            UserAddressEntity arg = invocation.getArgument(0);
                            arg.setAddressId(11L);
                            Instant now = Instant.parse("2026-11-12T02:05:00Z");
                            arg.setCreatedAt(now);
                            arg.setUpdatedAt(now);
                            return arg;
                        });

        UserAddressResponse response = useCase.execute("42", request);

        InOrder ordered = inOrder(userAddressRepository);
        ordered.verify(userAddressRepository).existsByCustomerIdAndIsDeletedFalse(42L);
        ordered.verify(userAddressRepository).clearDefaultByCustomerId(42L);
        ordered.verify(userAddressRepository).saveAndFlush(any(UserAddressEntity.class));

        assertThat(response.addressId()).isEqualTo(11L);
        assertThat(response.isDefault()).isTrue();
        assertThat(response.latitude().scale()).isEqualTo(7);
        assertThat(response.longitude().scale()).isEqualTo(7);
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID03 [N]: AC04 existing addresses with isDefault=false keeps existing default untouched")
    void execute_existingAddressesWithDefaultFalse_savesNonDefaultWithoutClearing() {
        var request =
                new AddUserAddressRequest(
                        "Nguyen Van C",
                        "0909998887",
                        "99 Vo Van Tan, District 3, HCMC",
                        new BigDecimal("10.7795000"),
                        new BigDecimal("106.6890000"),
                        false);

        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.existsByCustomerIdAndIsDeletedFalse(42L)).thenReturn(true);
        when(userAddressRepository.saveAndFlush(any(UserAddressEntity.class)))
                .thenAnswer(
                        invocation -> {
                            UserAddressEntity arg = invocation.getArgument(0);
                            arg.setAddressId(12L);
                            return arg;
                        });

        UserAddressResponse response = useCase.execute("42", request);

        verify(userAddressRepository, never()).clearDefaultByCustomerId(any());
        assertThat(response.addressId()).isEqualTo(12L);
        assertThat(response.isDefault()).isFalse();
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID04 [A]: AC01 blank, non-numeric or non-positive subject rejects before DB lookup")
    void execute_invalidSubject_throwsInvalidCredentialsWithoutDbQuery() {
        var request = sampleRequest(true);
        for (String subject :
                new String[] {null, "", "   ", "abc", "0", "-1", "9223372036854775808"}) {
            assertThatThrownBy(() -> useCase.execute(subject, request))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        verifyNoInteractions(userAccountRepository, userAddressRepository);
    }

    @Test
    @DisplayName("IAM-23 UTCID05 [A]: AC01 missing account in DB throws UserNotFoundException (404)")
    void execute_missingAccount_throwsUserNotFound() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("42", sampleRequest(true)))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName("IAM-23 UTCID06 [A]: AC01 non-ACTIVE account statuses throw AccessDeniedException (403)")
    void execute_inactiveAccountStatus_throwsAccessDenied() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));

        for (AccountStatus status : AccountStatus.values()) {
            if (status == AccountStatus.ACTIVE) {
                continue;
            }
            activeCustomerAccount.setStatus(status);
            assertThatThrownBy(() -> useCase.execute("42", sampleRequest(false)))
                    .isInstanceOf(AccessDeniedException.class);
        }

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID07 [A]: AC01 EMPLOYEE accountType throws AccessDeniedException (403) even when ACTIVE")
    void execute_employeeAccountType_throwsAccessDenied() {
        activeCustomerAccount.setAccountType(AccountType.EMPLOYEE);
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));

        assertThatThrownBy(() -> useCase.execute("42", sampleRequest(true)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName("IAM-23 UTCID08 [A]: AC05 persistence failure propagates cleanly without swallowing")
    void execute_databaseOutage_propagatesException() {
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.existsByCustomerIdAndIsDeletedFalse(42L)).thenReturn(false);
        when(userAddressRepository.saveAndFlush(any(UserAddressEntity.class)))
                .thenThrow(new DataAccessResourceFailureException("PostgreSQL unavailable"));

        assertThatThrownBy(() -> useCase.execute("42", sampleRequest(false)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID09 [B]: AC04 when all previous addresses were soft-deleted, newly added address auto-defaults to true")
    void execute_onlySoftDeletedHistoryAddresses_forcesDefaultTrue() {
        var request = sampleRequest(false);

        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(activeCustomerAccount));
        when(userAddressRepository.existsByCustomerIdAndIsDeletedFalse(42L)).thenReturn(false);
        when(userAddressRepository.saveAndFlush(any(UserAddressEntity.class)))
                .thenAnswer(
                        invocation -> {
                            UserAddressEntity arg = invocation.getArgument(0);
                            arg.setAddressId(20L);
                            return arg;
                        });

        UserAddressResponse response = useCase.execute("42", request);

        assertThat(response.addressId()).isEqualTo(20L);
        assertThat(response.isDefault()).isTrue();
        verify(userAddressRepository, never()).clearDefaultByCustomerId(any());
    }

    private AddUserAddressRequest sampleRequest(boolean isDefault) {
        return new AddUserAddressRequest(
                "Nguyen Van A",
                "0901234567",
                "123 Nguyen Hue, District 1, HCMC",
                new BigDecimal("10.7768891"),
                new BigDecimal("106.7008066"),
                isDefault);
    }
}
