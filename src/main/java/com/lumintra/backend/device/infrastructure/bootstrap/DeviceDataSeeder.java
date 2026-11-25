package com.lumintra.backend.device.infrastructure.bootstrap;

import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Seeds representative physical {@link LedInstanceEntity} warehouse, transit, bound, returned,
 * and decommissioned records after {@code IamDataSeeder}, {@code StoreDataSeeder}, and {@code CommerceDataSeeder} run.
 */
@Component
@Order(4)
@RequiredArgsConstructor
public class DeviceDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeviceDataSeeder.class);

    public static final int DEFAULT_IN_STOCK_PER_MODEL = 10;

    public static final String SEED_SERIAL_IN_STOCK_1 = "LMT-64X64-SEED-0001";
    public static final String SEED_SERIAL_IN_STOCK_2 = "LMT-64X64-SEED-0002";
    public static final String SEED_SERIAL_IN_TRANSIT = "LMT-32X64-SEED-0003";
    public static final String SEED_SERIAL_BOUND = "LMT-64X64-SEED-0004";
    public static final String SEED_SERIAL_RETURNED = "LMT-64X64-SEED-0005";
    public static final String SEED_SERIAL_DECOMMISSIONED = "LMT-64X64-SEED-0006";
    public static final String SEED_SERIAL_WARRANTY_CLEAN = "LMT-64X64-SEED-0007";
    public static final String SEED_SERIAL_WARRANTY_EXPIRED = "LMT-32X64-SEED-0008";
    public static final String SEED_SERIAL_CUST2_PENDING = "LMT-64X64-SEED-0009";
    public static final String SEED_SERIAL_CUST2_APPROVED = "LMT-32X64-SEED-0010";
    public static final String SEED_SERIAL_CUST3_COMPLETED = "LMT-64X64-SEED-0011";
    public static final String SEED_SERIAL_CUST4_REJECTED = "LMT-32X64-SEED-0012";
    public static final String SEED_SERIAL_CUST4_PENDING = "LMT-64X64-SEED-0013";

    private static final String SEED_ORDER_SHIPPING_SUFFIX = "SEED0004";
    private static final String SEED_ORDER_DELIVERED_SUFFIX = "SEED0005";
    private static final String SEED_ORDER_COMPLETED_SUFFIX = "SEED0007";
    private static final String SEED_ORDER_WARRANTY_CLEAN_SUFFIX = "SEED0012";
    private static final String SEED_ORDER_WARRANTY_EXPIRED_SUFFIX = "SEED0013";
    private static final String SEED_ORDER_CUST2_PENDING_SUFFIX = "SEED0014";
    private static final String SEED_ORDER_CUST2_APPROVED_SUFFIX = "SEED0015";
    private static final String SEED_ORDER_CUST3_COMPLETED_SUFFIX = "SEED0016";
    private static final String SEED_ORDER_CUST4_REJECTED_SUFFIX = "SEED0017";
    private static final String SEED_ORDER_CUST4_PENDING_SUFFIX = "SEED0018";

    private static final String SEED_CUSTOMER_2_EMAIL = "minhanh.nguyen@gmail.com";
    private static final String SEED_CUSTOMER_3_EMAIL = "quocbao.tran@gmail.com";
    private static final String SEED_CUSTOMER_4_EMAIL = "thaovy.le@gmail.com";

    private static final Set<String> LEGACY_CUSTOM_DEVICE_NAMES = Set.of(
            "Civic - Đèn kính sau LED",
            "Wave Alpha - Bảng LED mini",
            "Mazda 3 - Đèn kính sau",
            "Bàn làm việc Gaming RGB",
            "Quầy thu ngân Cafe Lumintra",
            "SH 150i - Biển LED sau xe",
            "VF e34 - Bảng LED chào mừng"
    );

    private final LedModelRepository ledModelRepository;
    private final LedInstanceRepository ledInstanceRepository;
    private final CustomerLookupService customerLookupService;
    private final OrderFulfillmentLookupService orderFulfillmentLookupService;

    @Value("${app.seed.test-customer-email:}")
    private String customerEmail;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking database for base LED instance seed data...");

        List<LedModelEntity> models = ledModelRepository.findAll(Sort.by(Sort.Direction.ASC, "modelId"));
        if (models.isEmpty()) {
            log.warn("No LED hardware models found in database. Skipping LED instance seed data.");
            return;
        }

        LedModelEntity primaryModel = models.get(0);
        LedModelEntity secondaryModel = models.size() > 1 ? models.get(1) : primaryModel;
        CustomerProfileSnapshot customer = resolveCustomer();

        Instant now = Instant.now();
        Instant manufacturedAt = now.minus(30, ChronoUnit.DAYS);

        int seededCount = 0;

        // Seed 10 IN_STOCK instances per LED Model
        for (LedModelEntity model : models) {
            Long modelId = model.getModelId();
            String prefix = sanitizeModelPrefix(model.getModelName());
            for (int i = 1; i <= 10; i++) {
                String serialNumber = String.format("LMT-%s-STOCK-%04d", prefix, i);
                String macAddress = generateMacAddress(modelId, i);
                seededCount += seedIfAbsent(LedInstanceEntity.createInStock(
                        modelId,
                        serialNumber,
                        macAddress,
                        "REV_1_0",
                        manufacturedAt.plus(i, ChronoUnit.DAYS)
                ), null);
            }
        }

        // Additional representative historical/lifecycle instances for testing
        seededCount += seedIfAbsent(LedInstanceEntity.createInStock(
                primaryModel.getModelId(),
                SEED_SERIAL_IN_STOCK_1,
                "AA:BB:CC:11:22:01",
                manufacturedAt
        ), null);
        seededCount += seedIfAbsent(LedInstanceEntity.createInStock(
                primaryModel.getModelId(),
                SEED_SERIAL_IN_STOCK_2,
                "AA:BB:CC:11:22:02",
                manufacturedAt.plus(2, ChronoUnit.DAYS)
        ), null);
        seededCount += seedIfAbsent(LedInstanceEntity.createWithState(
                secondaryModel.getModelId(),
                SEED_SERIAL_IN_TRANSIT,
                "24:D7:EB:18:24:0E",
                null,
                null,
                LedInstanceStatus.IN_TRANSIT,
                manufacturedAt,
                null,
                null
        ), SEED_ORDER_DELIVERED_SUFFIX);

        if (customer != null) {
            Instant boundAt = now.minus(5, ChronoUnit.DAYS);
            Instant warrantyExpiresAt = boundAt.plus(180, ChronoUnit.DAYS);
            seededCount += seedIfAbsent(LedInstanceEntity.createWithState(
                    primaryModel.getModelId(),
                    SEED_SERIAL_BOUND,
                    "AA:BB:CC:11:22:04",
                    customer.customerId(),
                    primaryModel.getModelName(),
                    LedInstanceStatus.BOUND,
                    manufacturedAt,
                    boundAt,
                    warrantyExpiresAt
            ), SEED_ORDER_COMPLETED_SUFFIX);
        }

        seededCount += seedIfAbsent(LedInstanceEntity.createWithState(
                primaryModel.getModelId(),
                SEED_SERIAL_RETURNED,
                "AA:BB:CC:11:22:05",
                null,
                null,
                LedInstanceStatus.RETURNED,
                manufacturedAt,
                null,
                null
        ), SEED_ORDER_SHIPPING_SUFFIX);
        seededCount += seedIfAbsent(LedInstanceEntity.createWithState(
                primaryModel.getModelId(),
                SEED_SERIAL_DECOMMISSIONED,
                "AA:BB:CC:11:22:06",
                null,
                null,
                LedInstanceStatus.DECOMMISSIONED,
                manufacturedAt,
                null,
                null
        ), null);

        seededCount += seedWarrantyBoundDevices(primaryModel, secondaryModel, customer, now);
        seededCount += seedModelWarehouseStockInstances(models, manufacturedAt);
        syncModelStocksWithInStockInstances(models);

        log.info("LED instance seed verification complete [seededCount={}]", seededCount);
    }

    /**
     * Ensures each seeded LED model has {@link #DEFAULT_IN_STOCK_PER_MODEL} physical IN_STOCK instances
     * so warehouse stock quantities align 1:1 with tracked hardware serial units.
     */
    private int seedModelWarehouseStockInstances(List<LedModelEntity> models, Instant manufacturedAt) {
        int seeded = 0;
        for (int modelIdx = 0; modelIdx < models.size(); modelIdx++) {
            LedModelEntity model = models.get(modelIdx);
            if (model.getModelId() == null || model.getStatus() == LedModelStatus.DEPRECATED) {
                continue;
            }
            String dimTag = resolveDimensionTag(model, modelIdx);
            // Primary model already seeds SEED_SERIAL_IN_STOCK_1 and SEED_SERIAL_IN_STOCK_2 (units 1 and 2)
            int startSeq = modelIdx == 0 ? 3 : 1;
            int macGroup = 0x21 + modelIdx;
            for (int seq = startSeq; seq <= DEFAULT_IN_STOCK_PER_MODEL; seq++) {
                String serial = String.format(Locale.ROOT, "LMT-%s-M%d-%04d", dimTag, model.getModelId(), seq);
                String mac = String.format(Locale.ROOT, "AA:BB:CC:%02X:00:%02X", macGroup, seq);
                seeded += seedIfAbsent(LedInstanceEntity.createInStock(
                        model.getModelId(),
                        serial,
                        mac,
                        manufacturedAt.plus(seq, ChronoUnit.DAYS)
                ), null);
            }
        }
        return seeded;
    }

    private void syncModelStocksWithInStockInstances(List<LedModelEntity> models) {
        for (LedModelEntity model : models) {
            if (model.getModelId() == null || model.getStatus() == LedModelStatus.DEPRECATED) {
                continue;
            }
            long inStockCount = ledInstanceRepository.countByModelIdAndStatus(
                    model.getModelId(), LedInstanceStatus.IN_STOCK);
            if (!Objects.equals(model.getStockQuantity(), inStockCount)) {
                model.setStockQuantity(inStockCount);
                model.setStatus(inStockCount > 0L ? LedModelStatus.ACTIVE : LedModelStatus.OUT_OF_STOCK);
                ledModelRepository.save(model);
                log.info("Synced LED model stockQuantity with IN_STOCK instances [modelId={}] [stockQuantity={}]",
                        model.getModelId(), inStockCount);
            }
        }
    }

    private String resolveDimensionTag(LedModelEntity model, int modelIdx) {
        if (model.getWidthPx() != null && model.getHeightPx() != null) {
            return model.getWidthPx() + "X" + model.getHeightPx();
        }
        return "M" + (modelIdx + 1);
    }

    private int seedWarrantyBoundDevices(
            LedModelEntity primaryModel,
            LedModelEntity secondaryModel,
            CustomerProfileSnapshot primaryCustomer,
            Instant now) {
        int seeded = 0;

        if (primaryCustomer != null) {
            seeded += seedBoundDevice(
                    primaryModel.getModelId(),
                    SEED_SERIAL_WARRANTY_CLEAN,
                    "AA:BB:CC:11:22:07",
                    primaryCustomer.customerId(),
                    primaryModel.getModelName(),
                    now.minus(35, ChronoUnit.DAYS),
                    now.minus(10, ChronoUnit.DAYS),
                    SEED_ORDER_WARRANTY_CLEAN_SUFFIX
            );
            seeded += seedBoundDevice(
                    secondaryModel.getModelId(),
                    SEED_SERIAL_WARRANTY_EXPIRED,
                    "AA:BB:CC:11:22:08",
                    primaryCustomer.customerId(),
                    secondaryModel.getModelName(),
                    now.minus(230, ChronoUnit.DAYS),
                    now.minus(200, ChronoUnit.DAYS),
                    SEED_ORDER_WARRANTY_EXPIRED_SUFFIX
            );
        }

        CustomerProfileSnapshot customer2 = customerLookupService.findProfileByEmail(SEED_CUSTOMER_2_EMAIL).orElse(null);
        if (customer2 != null) {
            seeded += seedBoundDevice(
                    primaryModel.getModelId(),
                    SEED_SERIAL_CUST2_PENDING,
                    "AA:BB:CC:11:22:09",
                    customer2.customerId(),
                    primaryModel.getModelName(),
                    now.minus(40, ChronoUnit.DAYS),
                    now.minus(15, ChronoUnit.DAYS),
                    SEED_ORDER_CUST2_PENDING_SUFFIX
            );
            seeded += seedBoundDevice(
                    secondaryModel.getModelId(),
                    SEED_SERIAL_CUST2_APPROVED,
                    "AA:BB:CC:11:22:10",
                    customer2.customerId(),
                    secondaryModel.getModelName(),
                    now.minus(50, ChronoUnit.DAYS),
                    now.minus(25, ChronoUnit.DAYS),
                    SEED_ORDER_CUST2_APPROVED_SUFFIX
            );
        }

        CustomerProfileSnapshot customer3 = customerLookupService.findProfileByEmail(SEED_CUSTOMER_3_EMAIL).orElse(null);
        if (customer3 != null) {
            seeded += seedBoundDevice(
                    primaryModel.getModelId(),
                    SEED_SERIAL_CUST3_COMPLETED,
                    "AA:BB:CC:11:22:11",
                    customer3.customerId(),
                    primaryModel.getModelName(),
                    now.minus(65, ChronoUnit.DAYS),
                    now.minus(40, ChronoUnit.DAYS),
                    SEED_ORDER_CUST3_COMPLETED_SUFFIX
            );
        }

        CustomerProfileSnapshot customer4 = customerLookupService.findProfileByEmail(SEED_CUSTOMER_4_EMAIL).orElse(null);
        if (customer4 != null) {
            seeded += seedBoundDevice(
                    secondaryModel.getModelId(),
                    SEED_SERIAL_CUST4_REJECTED,
                    "AA:BB:CC:11:22:12",
                    customer4.customerId(),
                    secondaryModel.getModelName(),
                    now.minus(35, ChronoUnit.DAYS),
                    now.minus(12, ChronoUnit.DAYS),
                    SEED_ORDER_CUST4_REJECTED_SUFFIX
            );
            seeded += seedBoundDevice(
                    primaryModel.getModelId(),
                    SEED_SERIAL_CUST4_PENDING,
                    "AA:BB:CC:11:22:13",
                    customer4.customerId(),
                    primaryModel.getModelName(),
                    now.minus(30, ChronoUnit.DAYS),
                    now.minus(7, ChronoUnit.DAYS),
                    SEED_ORDER_CUST4_PENDING_SUFFIX
            );
        }

        return seeded;
    }

    private int seedBoundDevice(
            Long modelId,
            String serialNumber,
            String macAddress,
            Long customerId,
            String deviceName,
            Instant manufacturedAt,
            Instant boundAt,
            String orderSuffix) {
        return seedIfAbsent(LedInstanceEntity.createWithState(
                modelId,
                serialNumber,
                macAddress,
                customerId,
                deviceName,
                LedInstanceStatus.BOUND,
                manufacturedAt,
                boundAt,
                boundAt.plus(180, ChronoUnit.DAYS)
        ), orderSuffix);
    }

    private String sanitizeModelPrefix(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return "LED";
        }
        String clean = modelName.replaceAll("[^a-zA-Z0-9]", "-")
                .replaceAll("-+", "-")
                .toUpperCase(java.util.Locale.ROOT);
        if (clean.startsWith("-")) {
            clean = clean.substring(1);
        }
        if (clean.endsWith("-")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean.length() > 20 ? clean.substring(0, 20) : clean;
    }

    private String generateMacAddress(Long modelId, int instanceIndex) {
        long mid = modelId != null ? modelId : 0L;
        int b1 = (int) ((mid >> 8) & 0xFF);
        int b2 = (int) (mid & 0xFF);
        int b3 = (instanceIndex >> 8) & 0xFF;
        int b4 = instanceIndex & 0xFF;
        return String.format("A2:B0:%02X:%02X:%02X:%02X", b1, b2, b3, b4);
    }

    private CustomerProfileSnapshot resolveCustomer() {
        if (customerEmail != null && !customerEmail.isBlank()) {
            return customerLookupService.findProfileByEmail(customerEmail.strip())
                    .or(customerLookupService::findFirstProfile)
                    .orElse(null);
        }
        return customerLookupService.findFirstProfile().orElse(null);
    }

    private int seedIfAbsent(LedInstanceEntity candidate, String orderTransactionSuffix) {
        if (ledInstanceRepository.existsBySerialNumber(candidate.getSerialNumber())) {
            ledInstanceRepository.findBySerialNumber(candidate.getSerialNumber()).ifPresent(existing -> {
                if (candidate.getDeviceName() != null
                        && existing.getDeviceName() != null
                        && LEGACY_CUSTOM_DEVICE_NAMES.contains(existing.getDeviceName())) {
                    existing.setDeviceName(candidate.getDeviceName());
                    ledInstanceRepository.save(existing);
                }
                if (orderTransactionSuffix != null && orderFulfillmentLookupService != null) {
                    orderFulfillmentLookupService.seedOrderFulfillmentIfPresent(
                            orderTransactionSuffix, existing.getInstanceId());
                }
            });
            return 0;
        }
        LedInstanceEntity saved = ledInstanceRepository.save(candidate);
        if (orderTransactionSuffix != null && orderFulfillmentLookupService != null && saved.getInstanceId() != null) {
            orderFulfillmentLookupService.seedOrderFulfillmentIfPresent(
                    orderTransactionSuffix, saved.getInstanceId());
        }
        log.info("Seeded LED instance [instanceId={}] [modelId={}] [serialNumber={}] [status={}]",
                saved.getInstanceId(), saved.getModelId(), saved.getSerialNumber(), saved.getStatus());
        return 1;
    }
}
