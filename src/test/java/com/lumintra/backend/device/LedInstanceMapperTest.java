package com.lumintra.backend.device;

import com.lumintra.backend.device.api.response.ClaimDeviceResponse;
import com.lumintra.backend.device.api.response.LedInstanceDetailResponse;
import com.lumintra.backend.device.api.response.LedInstanceListResponse;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.domain.model.WarrantyStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedInstanceMapper;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedModelMapper;
import com.lumintra.backend.device.infrastructure.security.HmacFactoryQrPayloadService;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LedInstanceMapperTest {

    private final HmacFactoryQrPayloadService qrService = new HmacFactoryQrPayloadService("mapper-test-secret");
    private final LedInstanceMapper mapper = new LedInstanceMapper(qrService, new LedModelMapper());

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

    @Test
    @DisplayName("DEV-174 MAP-01 [N]: Detail mapping includes full model specs, firmwareId, fulfillment, owner, and QR suppression rules")
    void toDetail_withModelAndSnapshots_mapsFullDetailAndSuppressesQrWhenBoundOrDecommissioned() {
        Instant manufacturedAt = Instant.parse("2026-10-15T06:00:00Z");
        Instant boundAt = Instant.parse("2026-11-01T10:00:00Z");
        Instant warrantyExpiresAt = Instant.parse("2027-04-30T10:00:00Z");
        Instant now = Instant.parse("2026-11-14T08:00:00Z");

        LedModelEntity modelEntity = LedModelEntity.createCatalogModel(
                101L,
                101L,
                "Lumintra Matrix 64x64 Pro",
                64,
                64,
                Map.of("attributes", List.of(
                        Map.of("label", "Voltage", "value", "5V USB-C"),
                        Map.of("label", "Brightness", "value", "1200 nits")
                )),
                50L,
                LedModelStatus.ACTIVE
        );

        LedInstanceEntity boundEntity = LedInstanceEntity.createWithState(
                101L,
                "LMT-64X64-0001",
                "AA:BB:CC:11:22:01",
                "REV_1_0",
                42L,
                "Windshield Pro",
                LedInstanceStatus.BOUND,
                manufacturedAt,
                boundAt,
                warrantyExpiresAt
        );
        boundEntity.setCurrentFirmwareId(12L);
        ReflectionTestUtils.setField(boundEntity, "instanceId", 7L);

        LedInstanceDetailResponse detail = mapper.toDetail(
                boundEntity,
                modelEntity,
                new CustomerProfileSnapshot(42L, "Nguyen Van A", "0901234567", "customer@example.com"),
                new InstanceFulfillmentSnapshot(7L, 55L, 102L),
                now
        );

        assertThat(detail.instanceId()).isEqualTo(7L);
        assertThat(detail.currentFirmwareId()).isEqualTo(12L);
        assertThat(detail.status()).isEqualTo(LedInstanceStatus.BOUND);
        assertThat(detail.warrantyStatus()).isEqualTo(WarrantyStatus.ACTIVE);
        assertThat(detail.qrPayload()).isNull();
        assertThat(detail.model().modelId()).isEqualTo(101L);
        assertThat(detail.model().productId()).isEqualTo(101L);
        assertThat(detail.model().modelName()).isEqualTo("Lumintra Matrix 64x64 Pro");
        assertThat(detail.model().status()).isEqualTo(LedModelStatus.ACTIVE);
        assertThat(detail.model().spec().widthPx()).isEqualTo(64);
        assertThat(detail.model().spec().heightPx()).isEqualTo(64);
        assertThat(detail.model().spec().attributes()).containsExactly(
                new LedModelListResponse.SpecAttribute("Voltage", "5V USB-C"),
                new LedModelListResponse.SpecAttribute("Brightness", "1200 nits")
        );
        assertThat(detail.fulfillment()).isEqualTo(new LedInstanceDetailResponse.FulfillmentInfo(55L, 102L));
        assertThat(detail.owner()).isEqualTo(new LedInstanceDetailResponse.OwnerInfo(
                42L, "Nguyen Van A", "0901234567", "customer@example.com"));

        for (LedInstanceStatus status : LedInstanceStatus.values()) {
            LedInstanceEntity candidate = LedInstanceEntity.createWithState(
                    101L, "LMT-" + status.name(), "AA:BB:CC:00:00:02", "REV_1_0",
                    null, null, status, manufacturedAt, null, null);
            ReflectionTestUtils.setField(candidate, "instanceId", 8L);

            LedInstanceDetailResponse mapped = mapper.toDetail(candidate, null, null, null, now);
            if (status == LedInstanceStatus.BOUND || status == LedInstanceStatus.DECOMMISSIONED) {
                assertThat(mapped.qrPayload()).isNull();
            } else {
                assertThat(mapped.qrPayload()).isEqualTo(
                        qrService.generateQrPayload("LMT-" + status.name(), "AA:BB:CC:00:00:02"));
            }
            assertThat(mapped.model()).isEqualTo(
                    LedInstanceDetailResponse.ModelInfo.builder().modelId(101L).build());
        }
    }

    @Test
    @DisplayName("DEV-174 MAP-02 [B]: Computes NOT_ACTIVATED, ACTIVE (including expiry boundary), and EXPIRED warranty status")
    void toDetail_warrantyBoundaries_computesExpectedStatus() {
        Instant boundAt = Instant.parse("2026-11-01T10:00:00Z");
        Instant expiry = Instant.parse("2027-04-30T10:00:00Z");

        LedInstanceEntity unactivated = LedInstanceEntity.createInStock(
                101L, "LMT-01", "AA:BB:CC:00:00:11", "REV_1_0", Instant.parse("2026-10-01T00:00:00Z"));
        ReflectionTestUtils.setField(unactivated, "instanceId", 1L);

        LedInstanceEntity activated = LedInstanceEntity.createWithState(
                101L, "LMT-02", "AA:BB:CC:00:00:12", "REV_1_0", 42L, "Rear",
                LedInstanceStatus.BOUND, Instant.parse("2026-10-01T00:00:00Z"), boundAt, expiry);
        ReflectionTestUtils.setField(activated, "instanceId", 2L);

        assertThat(mapper.toDetail(unactivated, null, null, null, expiry).warrantyStatus())
                .isEqualTo(WarrantyStatus.NOT_ACTIVATED);
        assertThat(mapper.toDetail(activated, null, null, null, expiry.minusSeconds(1)).warrantyStatus())
                .isEqualTo(WarrantyStatus.ACTIVE);
        assertThat(mapper.toDetail(activated, null, null, null, expiry).warrantyStatus())
                .isEqualTo(WarrantyStatus.ACTIVE);
        assertThat(mapper.toDetail(activated, null, null, null, expiry.plusSeconds(1)).warrantyStatus())
                .isEqualTo(WarrantyStatus.EXPIRED);
    }

    @Test
    @DisplayName("DEV-93 MAP-03 [N]: Maps bound LED instance and model into ClaimDeviceResponse")
    void toClaimResponse_mapsDeviceAndModelSpecFields() {
        Instant boundAt = Instant.parse("2026-11-14T08:00:00Z");
        Instant warrantyExpiresAt = Instant.parse("2027-05-13T08:00:00Z");

        LedInstanceEntity entity = LedInstanceEntity.createWithState(
                101L,
                "LMT-64X64-0005",
                "aa:bb:cc:11:22:05",
                "REV_1_0",
                42L,
                "Rear Window Matrix",
                LedInstanceStatus.BOUND,
                Instant.parse("2026-10-15T08:00:00Z"),
                boundAt,
                warrantyExpiresAt
        );
        ReflectionTestUtils.setField(entity, "instanceId", 501L);

        LedModelEntity modelEntity = LedModelEntity.createCatalogModel(
                101L,
                101L,
                "Lumintra Matrix 64x64 Pro",
                64,
                64,
                Map.of("attributes", List.of(Map.of("label", "Voltage", "value", "5V USB-C"))),
                50L,
                LedModelStatus.ACTIVE
        );

        ClaimDeviceResponse response = mapper.toClaimResponse(entity, modelEntity);

        assertThat(response.deviceId()).isEqualTo(501L);
        assertThat(response.serialNumber()).isEqualTo("LMT-64X64-0005");
        assertThat(response.macAddress()).isEqualTo("AA:BB:CC:11:22:05");
        assertThat(response.deviceName()).isEqualTo("Rear Window Matrix");
        assertThat(response.modelId()).isEqualTo(101L);
        assertThat(response.modelName()).isEqualTo("Lumintra Matrix 64x64 Pro");
        assertThat(response.spec().widthPx()).isEqualTo(64);
        assertThat(response.spec().heightPx()).isEqualTo(64);
        assertThat(response.spec().attributes()).containsExactly(
                new LedModelListResponse.SpecAttribute("Voltage", "5V USB-C")
        );
        assertThat(response.boundAt()).isEqualTo(boundAt);
        assertThat(response.warrantyExpiresAt()).isEqualTo(warrantyExpiresAt);
    }
}
