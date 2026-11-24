package com.lumintra.backend.device.infrastructure.bootstrap;

import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.WarrantyTicketEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.WarrantyTicketRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds representative {@link WarrantyTicketEntity} records across all lifecycle statuses
 * ({@code PENDING}, {@code APPROVED}, {@code COMPLETED}, {@code REJECTED}) linked to seeded
 * LED hardware instances, customers, and commerce order items.
 */
@Component
@Order(7)
@RequiredArgsConstructor
public class WarrantyDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WarrantyDataSeeder.class);

    private final WarrantyTicketRepository warrantyTicketRepository;
    private final LedInstanceRepository ledInstanceRepository;
    private final LedModelRepository ledModelRepository;
    private final OrderFulfillmentLookupService orderFulfillmentLookupService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking database for base Warranty ticket seed data...");

        if (warrantyTicketRepository.count() > 0) {
            log.info("Warranty ticket seed data already exists [count={}]. Skipping.",
                    warrantyTicketRepository.count());
            return;
        }

        int seededCount = 0;

        // 1. Primary customer historical completed claim (keeps LMT-64X64-SEED-0007 eligible for a new claim)
        seededCount += seedTicketIfDevicePresent(
                DeviceDataSeeder.SEED_SERIAL_BOUND,
                WarrantyTicketStatus.COMPLETED,
                "Màn hình {modelName} bị chớp nháy nhẹ ở góc trên bên phải sau khi cập nhật firmware.",
                List.of("https://placehold.co/800x600/0f172a/34d399/png?text=LED+Corner+Flicker"),
                "Đã kiểm tra và nạp lại firmware hiệu chuẩn độ sáng cho mẫu {modelName}, test chạy ổn định 24h và bàn giao lại cho khách."
        );

        // 2. Customer 3 completed replacement/repair claim
        seededCount += seedTicketIfDevicePresent(
                DeviceDataSeeder.SEED_SERIAL_CUST3_COMPLETED,
                WarrantyTicketStatus.COMPLETED,
                "Có 3 điểm ảnh chết màu xanh dương ở vùng trung tâm màn hình {modelName}.",
                List.of(
                        "https://placehold.co/800x600/111827/60a5fa/png?text=Dead+Blue+Pixels",
                        "https://placehold.co/800x600/1e293b/93c5fd/png?text=Matrix+Center+View"
                ),
                "Đã thay thế tấm LED ma trận HUB75 chính hãng cho mẫu {modelName} và kiểm tra cân bằng màu RGB đạt chuẩn trước khi gửi trả khách."
        );

        // 3. Customer 4 rejected claim (water ingress / broken warranty seal)
        seededCount += seedTicketIfDevicePresent(
                DeviceDataSeeder.SEED_SERIAL_CUST4_REJECTED,
                WarrantyTicketStatus.REJECTED,
                "Bảng {modelName} không lên nguồn sau khi vô tình đổ nước lên bộ điều khiển.",
                List.of("https://placehold.co/800x600/1f2937/f87171/png?text=Water+Damage+Board"),
                "Từ chối bảo hành: Sau khi kiểm tra hình ảnh bằng chứng của mẫu {modelName}, phát hiện dấu hiệu nước xâm nhập bo mạch điều khiển và rách tem niêm phong bảo hành (không thuộc phạm vi bảo hành phần cứng tiêu chuẩn)."
        );

        // 4. Customer 2 approved claim (waiting at counter for repair/exchange)
        seededCount += seedTicketIfDevicePresent(
                DeviceDataSeeder.SEED_SERIAL_CUST2_APPROVED,
                WarrantyTicketStatus.APPROVED,
                "Cổng nguồn 5V USB-C trên mạch điều khiển mẫu {modelName} bị lỏng, phải giữ dây mới lên màn hình.",
                List.of("https://placehold.co/800x600/0f172a/38bdf8/png?text=USBC+Port+Loose"),
                "Đã duyệt tiếp nhận bảo hành cho mẫu {modelName}. Vui lòng mang hoặc gửi thiết bị về Trung tâm Bảo hành Lumintra (123 Lê Lợi, Q.1, TP.HCM) kèm mã phiếu."
        );

        // 5. Customer 2 pending claim (newest queue)
        seededCount += seedTicketIfDevicePresent(
                DeviceDataSeeder.SEED_SERIAL_CUST2_PENDING,
                WarrantyTicketStatus.PENDING,
                "Màn hình {modelName} bị sọc ngang ở hàng điểm ảnh số 4 và tự ngắt nguồn sau 10 phút hoạt động.",
                List.of(
                        "https://placehold.co/800x600/111827/fbbf24/png?text=LED+Horizontal+Line",
                        "https://placehold.co/800x600/1f2937/fcd34d/png?text=Power+Drop+Issue"
                ),
                null
        );

        // 6. Customer 4 pending claim (newest queue)
        seededCount += seedTicketIfDevicePresent(
                DeviceDataSeeder.SEED_SERIAL_CUST4_PENDING,
                WarrantyTicketStatus.PENDING,
                "Thiết bị {modelName} không kết nối được Bluetooth/Wi-Fi với ứng dụng Lumintra dù đèn báo nguồn mạch điều khiển vẫn sáng.",
                List.of("https://placehold.co/800x600/0f172a/a78bfa/png?text=BLE+Connection+Error"),
                null
        );

        log.info("Warranty ticket seed verification complete [seededCount={}]", seededCount);
    }

    private int seedTicketIfDevicePresent(
            String serialNumber,
            WarrantyTicketStatus targetStatus,
            String issueDescriptionTemplate,
            List<String> evidenceMediaUrls,
            String resolutionNotesTemplate) {
        LedInstanceEntity instance = ledInstanceRepository.findBySerialNumber(serialNumber).orElse(null);
        if (instance == null || instance.getOwnerCustomerId() == null) {
            log.debug("Skipping warranty ticket seed; bound LED instance not found [serialNumber={}]", serialNumber);
            return 0;
        }

        LedModelEntity model = instance.getModelId() != null
                ? ledModelRepository.findById(instance.getModelId()).orElse(null)
                : null;
        if (model == null) {
            log.debug("Skipping warranty ticket seed; linked LED model not found [serialNumber={}] [modelId={}]",
                    serialNumber, instance.getModelId());
            return 0;
        }

        String modelName = model.getModelName();
        String issueDescription = issueDescriptionTemplate.replace("{modelName}", modelName);
        String resolutionNotes = resolutionNotesTemplate != null
                ? resolutionNotesTemplate.replace("{modelName}", modelName)
                : null;

        Long orderItemId = orderFulfillmentLookupService != null && instance.getInstanceId() != null
                ? orderFulfillmentLookupService.findFulfillmentByInstanceId(instance.getInstanceId())
                        .map(InstanceFulfillmentSnapshot::orderItemId)
                        .orElse(null)
                : null;

        WarrantyTicketEntity ticket = WarrantyTicketEntity.createPending(
                instance.getOwnerCustomerId(),
                orderItemId,
                instance.getSerialNumber(),
                issueDescription,
                evidenceMediaUrls
        );

        switch (targetStatus) {
            case APPROVED -> ticket.approve(resolutionNotes);
            case COMPLETED -> ticket.complete(resolutionNotes);
            case REJECTED -> ticket.reject(resolutionNotes);
            case PENDING -> {
                // Remains PENDING
            }
        }

        WarrantyTicketEntity saved = warrantyTicketRepository.save(ticket);
        log.info("Seeded warranty ticket [ticketId={}] [serialNumber={}] [customerId={}] [orderItemId={}] [status={}]",
                saved.getTicketId(), saved.getDeviceSerialNumber(), saved.getCustomerId(),
                saved.getOrderItemId(), saved.getStatus());
        return 1;
    }
}
