package com.lumintra.backend.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumintra.backend.iam.api.response.UserAddressResponse;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAddressEntity;
import com.lumintra.backend.iam.infrastructure.persistence.mapper.UserAddressMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

class UserAddressMapperTest {

    private final UserAddressMapper mapper = new UserAddressMapper();

    @Test
    @DisplayName("IAM-22 UTCID01 [N]: AC04 maps entity list to UserAddressResponse preserving coordinates, isDefault and timestamps")
    void toResponseList_mapsEntityFieldsWithoutCustomerId() {
        Instant createdAt = Instant.parse("2026-11-10T02:15:00Z");
        Instant updatedAt = Instant.parse("2026-11-11T04:20:00Z");
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
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);

        List<UserAddressResponse> responses = mapper.toResponseList(List.of(entity));

        assertThat(responses).hasSize(1);
        UserAddressResponse item = responses.getFirst();
        assertThat(item.addressId()).isEqualTo(10L);
        assertThat(item.recipientName()).isEqualTo("Nguyen Van A");
        assertThat(item.recipientPhone()).isEqualTo("0901234567");
        assertThat(item.fullAddress()).isEqualTo("123 Nguyen Hue, Ben Nghe, District 1, HCMC");
        assertThat(item.latitude()).isEqualByComparingTo("10.7768890");
        assertThat(item.longitude()).isEqualByComparingTo("106.7008060");
        assertThat(item.isDefault()).isTrue();
        assertThat(item.createdAt()).isEqualTo(createdAt);
        assertThat(item.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("IAM-22 UTCID02 [B]: AC04 handles null or empty input lists and null entity safely")
    void toResponseList_handlesEmptyAndNullInputs() {
        assertThat(mapper.toResponseList(null)).isEmpty();
        assertThat(mapper.toResponseList(List.of())).isEmpty();
        assertThat(mapper.toResponse(null)).isNull();
    }
}
