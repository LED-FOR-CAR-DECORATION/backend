package com.lumintra.backend.device;

import com.lumintra.backend.device.api.response.LedInstanceListResponse;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedInstanceMapper;
import com.lumintra.backend.device.infrastructure.security.HmacFactoryQrPayloadService;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LedInstanceMapperTest {

    private final HmacFactoryQrPayloadService qrService = new HmacFactoryQrPayloadService("mapper-test-secret");
    private final LedInstanceMapper mapper = new LedInstanceMapper(qrService);

    @Test
    @DisplayName("DEV-173 MAP-01 [N]: Returns signed QR payload for IN_STOCK, IN_TRANSIT, RETURNED and suppresses for BOUND and DECOMMISSIONED")
    void toItem_qrSuppressionByStatus_appliesRules() {
        for (LedInstanceStatus status : LedInstanceStatus.values()) {
            LedInstanceEntity entity = LedInstanceEntity.createWithState(
                    101L,
                    "LMT-" + status.name(),
                    "AA:BB:CC:00:00:01",
                    "REV_1_0",
                    null,
                    null,
                    status,
                    Instant.parse("2026-10-01T00:00:00Z"),
                    null,
                    null
            );
            ReflectionTestUtils.setField(entity, "instanceId", 1L);

            LedInstanceListResponse.LedInstanceItem item = mapper.toItem(entity, Map.of(), Map.of(), Map.of());

            if (status == LedInstanceStatus.BOUND || status == LedInstanceStatus.DECOMMISSIONED) {
                assertThat(item.qrPayload())
                        .as("QR payload must be null for status %s", status)
                        .isNull();
            } else {
                assertThat(item.qrPayload())
                        .as("QR payload must be generated for status %s", status)
                        .isEqualTo(qrService.generateQrPayload("LMT-" + status.name(), "AA:BB:CC:00:00:01"));
            }
        }
    }

    @Test
    @DisplayName("DEV-173 MAP-02 [N]: Enriches modelName, owner info, fulfillment info, and warranty dates")
    void toItem_withEnrichmentSnapshots_mapsAllNestedSections() {
        Instant boundAt = Instant.parse("2026-11-01T10:00:00Z");
        Instant warrantyExpiresAt = Instant.parse("2027-04-30T10:00:00Z");
        Instant manufacturedAt = Instant.parse("2026-09-15T08:00:00Z");

        LedInstanceEntity entity = LedInstanceEntity.createWithState(
                101L,
                "LMT-64X64-0099",
                "aa:bb:cc:99:88:77",
                "REV_2_0",
                42L,
                "Front Windshield",
                LedInstanceStatus.BOUND,
                manufacturedAt,
                boundAt,
                warrantyExpiresAt
        );
        ReflectionTestUtils.setField(entity, "instanceId", 77L);

        LedModelSnapshot modelSnapshot = new LedModelSnapshot(101L, "Lumintra Matrix 64x64 Pro", Map.of(), 50L);
        CustomerProfileSnapshot customerSnapshot = new CustomerProfileSnapshot(
                42L, "Nguyen Van A", "0901234567", "customer@example.com");
        InstanceFulfillmentSnapshot fulfillmentSnapshot = new InstanceFulfillmentSnapshot(77L, 500L, 900L);

        LedInstanceListResponse.LedInstanceItem item = mapper.toItem(
                entity,
                Map.of(101L, modelSnapshot),
                Map.of(42L, customerSnapshot),
                Map.of(77L, fulfillmentSnapshot)
        );

        assertThat(item.instanceId()).isEqualTo(77L);
        assertThat(item.modelId()).isEqualTo(101L);
        assertThat(item.modelName()).isEqualTo("Lumintra Matrix 64x64 Pro");
        assertThat(item.macAddress()).isEqualTo("AA:BB:CC:99:88:77");
        assertThat(item.hardwareRevision()).isEqualTo("REV_2_0");
        assertThat(item.deviceName()).isEqualTo("Front Windshield");
        assertThat(item.status()).isEqualTo(LedInstanceStatus.BOUND);
        assertThat(item.qrPayload()).isNull();
        assertThat(item.boundAt()).isEqualTo(boundAt);
        assertThat(item.warrantyExpiresAt()).isEqualTo(warrantyExpiresAt);
        assertThat(item.manufacturedAt()).isEqualTo(manufacturedAt);
        assertThat(item.fulfillment()).isEqualTo(new LedInstanceListResponse.FulfillmentInfo(500L, 900L));
        assertThat(item.owner()).isEqualTo(new LedInstanceListResponse.OwnerInfo(
                42L, "Nguyen Van A", "0901234567", "customer@example.com"));
    }

    @Test
    @DisplayName("DEV-173 MAP-03 [B]: Falls back cleanly when lookup maps are null or missing profile/fulfillment entries")
    void toItem_missingLookupSnapshots_preservesOwnerIdAndNullSectionsSafely() {
        LedInstanceEntity entity = LedInstanceEntity.createWithState(
                101L,
                "LMT-64X64-0100",
                "AA:BB:CC:00:00:10",
                "REV_1_0",
                88L,
                null,
                LedInstanceStatus.RETURNED,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(entity, "instanceId", 80L);

        LedInstanceListResponse.LedInstanceItem item = mapper.toItem(entity, null, Map.of(), null);

        assertThat(item.modelName()).isNull();
        assertThat(item.fulfillment()).isNull();
        assertThat(item.owner()).isEqualTo(new LedInstanceListResponse.OwnerInfo(88L, null, null, null));
        assertThat(item.qrPayload()).isNotNull();
    }
}
