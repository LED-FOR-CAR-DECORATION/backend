package com.lumintra.backend.device;

import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import com.lumintra.backend.device.infrastructure.bootstrap.DeviceDataSeeder;
import com.lumintra.backend.device.infrastructure.bootstrap.WarrantyDataSeeder;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.WarrantyTicketEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.WarrantyTicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WarrantyDataSeederTest {

    @Mock
    private WarrantyTicketRepository warrantyTicketRepository;

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    @Mock
    private LedModelRepository ledModelRepository;

    @Mock
    private OrderFulfillmentLookupService orderFulfillmentLookupService;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private WarrantyDataSeeder seeder;

    @Captor
    private ArgumentCaptor<WarrantyTicketEntity> ticketCaptor;

    @Test
    @DisplayName("N: Clean database with bound LED devices seeds 6 warranty tickets across all statuses with orderItemId links")
    void run_cleanDatabaseWithBoundDevices_seedsAllWarrantyTickets() {
        given(warrantyTicketRepository.count()).willReturn(0L);

        mockBoundDevice(10L, DeviceDataSeeder.SEED_SERIAL_BOUND, 1L,
                "Tấm LED Lumintra 64x64 Cao Cấp", 1L, 1001L);
        mockBoundDevice(11L, DeviceDataSeeder.SEED_SERIAL_CUST3_COMPLETED, 1L,
                "Tấm LED Lumintra 64x64 Cao Cấp", 3L, 1002L);
        mockBoundDevice(12L, DeviceDataSeeder.SEED_SERIAL_CUST4_REJECTED, 2L,
                "Tấm LED Lumintra 64x32 Gọn Nhẹ", 4L, 1003L);
        mockBoundDevice(13L, DeviceDataSeeder.SEED_SERIAL_CUST2_APPROVED, 2L,
                "Tấm LED Lumintra 64x32 Gọn Nhẹ", 2L, 1004L);
        mockBoundDevice(14L, DeviceDataSeeder.SEED_SERIAL_CUST2_PENDING, 1L,
                "Tấm LED Lumintra 64x64 Cao Cấp", 2L, 1005L);
        mockBoundDevice(15L, DeviceDataSeeder.SEED_SERIAL_CUST4_PENDING, 1L,
                "Tấm LED Lumintra 64x64 Cao Cấp", 4L, 1006L);

        AtomicLong ticketIds = new AtomicLong(1L);
        given(warrantyTicketRepository.save(any(WarrantyTicketEntity.class))).willAnswer(inv -> {
            WarrantyTicketEntity saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "ticketId", ticketIds.getAndIncrement());
            return saved;
        });

        seeder.run(applicationArguments);

        verify(warrantyTicketRepository, times(6)).save(ticketCaptor.capture());
        List<WarrantyTicketEntity> savedTickets = ticketCaptor.getAllValues();

        assertThat(savedTickets)
                .extracting(WarrantyTicketEntity::getStatus)
                .containsExactly(
                        WarrantyTicketStatus.COMPLETED,
                        WarrantyTicketStatus.COMPLETED,
                        WarrantyTicketStatus.REJECTED,
                        WarrantyTicketStatus.APPROVED,
                        WarrantyTicketStatus.PENDING,
                        WarrantyTicketStatus.PENDING
                );
        assertThat(savedTickets)
                .extracting(WarrantyTicketEntity::getOrderItemId)
                .containsExactly(1001L, 1002L, 1003L, 1004L, 1005L, 1006L);
        assertThat(savedTickets)
                .allSatisfy(ticket -> {
                    String expectedModelName = ticket.getDeviceSerialNumber().startsWith("LMT-64X32-")
                            ? "Tấm LED Lumintra 64x32 Gọn Nhẹ"
                            : "Tấm LED Lumintra 64x64 Cao Cấp";
                    assertThat(ticket.getIssueDescription()).contains(expectedModelName);
                    assertThat(ticket.getEvidenceMediaUrls()).isNotEmpty();
                    if (ticket.getStatus() != WarrantyTicketStatus.PENDING) {
                        assertThat(ticket.getResolutionNotes()).contains(expectedModelName);
                    }
                });
    }

    @Test
    @DisplayName("B: Skips seeding idempotently when warranty tickets already exist")
    void run_existingTickets_skipsIdempotently() {
        given(warrantyTicketRepository.count()).willReturn(3L);

        seeder.run(applicationArguments);

        verifyNoInteractions(ledInstanceRepository);
        verify(warrantyTicketRepository, never()).save(any());
    }

    @Test
    @DisplayName("B: Skips devices that are missing or unbound safely")
    void run_missingOrUnboundDevices_skipsSafely() {
        given(warrantyTicketRepository.count()).willReturn(0L);
        given(ledInstanceRepository.findBySerialNumber(any())).willReturn(Optional.empty());

        seeder.run(applicationArguments);

        verify(warrantyTicketRepository, never()).save(any());
    }

    private void mockBoundDevice(
            Long instanceId,
            String serial,
            Long modelId,
            String modelName,
            Long customerId,
            Long orderItemId) {
        Instant now = Instant.now();
        LedInstanceEntity entity = LedInstanceEntity.createWithState(
                modelId,
                serial,
                "AA:BB:CC:11:22:" + instanceId,
                customerId,
                modelName,
                LedInstanceStatus.BOUND,
                now.minus(30, ChronoUnit.DAYS),
                now.minus(10, ChronoUnit.DAYS),
                now.plus(170, ChronoUnit.DAYS)
        );
        ReflectionTestUtils.setField(entity, "instanceId", instanceId);
        LedModelEntity model = LedModelEntity.createForProduct(100L + modelId, modelName, java.util.Map.of(), 50L);
        ReflectionTestUtils.setField(model, "modelId", modelId);
        given(ledModelRepository.findById(modelId)).willReturn(Optional.of(model));
        given(ledInstanceRepository.findBySerialNumber(serial)).willReturn(Optional.of(entity));
        given(orderFulfillmentLookupService.findFulfillmentByInstanceId(instanceId))
                .willReturn(Optional.of(new InstanceFulfillmentSnapshot(
                        instanceId, 500L + instanceId, orderItemId, customerId, "COMPLETED")));
    }
}
