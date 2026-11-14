package com.lumintra.backend.device.infrastructure.bootstrap;

import com.lumintra.backend.device.domain.model.LedInstanceStatus;
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

/**
 * Seeds representative physical {@link LedInstanceEntity} warehouse, transit, bound, returned,
 * and decommissioned records after {@code IamDataSeeder}, {@code StoreDataSeeder}, and {@code CommerceDataSeeder} run.
 */
@Component
@Order(4)
@RequiredArgsConstructor
public class DeviceDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeviceDataSeeder.class);

    public static final String SEED_SERIAL_IN_STOCK_1 = "LMT-64X64-SEED-0001";
    public static final String SEED_SERIAL_IN_STOCK_2 = "LMT-64X64-SEED-0002";
    public static final String SEED_SERIAL_IN_TRANSIT = "LMT-32X64-SEED-0003";
    public static final String SEED_SERIAL_BOUND = "LMT-64X64-SEED-0004";
    public static final String SEED_SERIAL_RETURNED = "LMT-64X64-SEED-0005";
    public static final String SEED_SERIAL_DECOMMISSIONED = "LMT-64X64-SEED-0006";

    private static final String SEED_ORDER_SHIPPING_SUFFIX = "SEED0004";
    private static final String SEED_ORDER_DELIVERED_SUFFIX = "SEED0005";
    private static final String SEED_ORDER_COMPLETED_SUFFIX = "SEED0007";

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
        seededCount += seedIfAbsent(LedInstanceEntity.createInStock(
                primaryModel.getModelId(),
                SEED_SERIAL_IN_STOCK_1,
                "AA:BB:CC:11:22:01",
                "REV_1_0",
                manufacturedAt
        ), null);
        seededCount += seedIfAbsent(LedInstanceEntity.createInStock(
                primaryModel.getModelId(),
                SEED_SERIAL_IN_STOCK_2,
                "AA:BB:CC:11:22:02",
                "REV_1_0",
                manufacturedAt.plus(2, ChronoUnit.DAYS)
        ), null);
        seededCount += seedIfAbsent(LedInstanceEntity.createWithState(
                secondaryModel.getModelId(),
                SEED_SERIAL_IN_TRANSIT,
                "AA:BB:CC:11:22:03",
                "REV_1_0",
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
                    "REV_1_0",
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
                "REV_1_0",
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
                "REV_1_0",
                null,
                null,
                LedInstanceStatus.DECOMMISSIONED,
                manufacturedAt,
                null,
                null
        ), null);

        log.info("LED instance seed verification complete [seededCount={}]", seededCount);
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
            if (orderTransactionSuffix != null && orderFulfillmentLookupService != null) {
                ledInstanceRepository.findBySerialNumber(candidate.getSerialNumber())
                        .map(LedInstanceEntity::getInstanceId)
                        .ifPresent(id -> orderFulfillmentLookupService
                                .seedOrderFulfillmentIfPresent(orderTransactionSuffix, id));
            }
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
