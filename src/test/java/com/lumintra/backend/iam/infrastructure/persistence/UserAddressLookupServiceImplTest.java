package com.lumintra.backend.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumintra.backend.iam.domain.model.UserAddressSnapshot;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAddressEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class UserAddressLookupServiceImplTest {

    @Mock private UserAddressRepository userAddressRepository;

    private UserAddressLookupServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAddressLookupServiceImpl(userAddressRepository);
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID01 [N]: AC04 active owned address maps to UserAddressSnapshot for Checkout")
    void findByIdAndCustomerId_activeOwnedAddress_returnsMappedSnapshot() {
        UserAddressEntity entity =
                UserAddressEntity.of(
                        10L,
                        42L,
                        "Nguyen Van A",
                        "0901234567",
                        "123 Nguyen Hue, Ben Nghe, District 1, HCMC",
                        new BigDecimal("10.7768890"),
                        new BigDecimal("106.7008060"),
                        true);
        when(userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(10L, 42L))
                .thenReturn(Optional.of(entity));

        Optional<UserAddressSnapshot> result = service.findByIdAndCustomerId(10L, 42L);

        assertThat(result)
                .contains(
                        new UserAddressSnapshot(
                                10L,
                                42L,
                                "Nguyen Van A",
                                "0901234567",
                                "123 Nguyen Hue, Ben Nghe, District 1, HCMC",
                                new BigDecimal("10.7768890"),
                                new BigDecimal("106.7008060")));
        verify(userAddressRepository).findByAddressIdAndCustomerIdAndIsDeletedFalse(10L, 42L);
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID02 [B]: AC04 null addressId or customerId returns empty without querying repository")
    void findByIdAndCustomerId_nullArguments_returnsEmptyWithoutDbQuery() {
        assertThat(service.findByIdAndCustomerId(null, 42L)).isEmpty();
        assertThat(service.findByIdAndCustomerId(10L, null)).isEmpty();
        verifyNoInteractions(userAddressRepository);
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID03 [A]: AC04 missing, soft-deleted, or foreign customer address returns empty")
    void findByIdAndCustomerId_softDeletedOrMissing_returnsEmpty() {
        when(userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(10L, 42L))
                .thenReturn(Optional.empty());

        assertThat(service.findByIdAndCustomerId(10L, 42L)).isEmpty();
        verify(userAddressRepository).findByAddressIdAndCustomerIdAndIsDeletedFalse(10L, 42L);
    }
}
