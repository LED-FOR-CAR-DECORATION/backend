package com.lumintra.backend.device;

import com.lumintra.backend.device.api.response.CustomerDeviceWarrantyResponse;
import com.lumintra.backend.device.application.GetCustomerDeviceWarrantyUseCase;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.WarrantyStatus;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.WarrantyTicketEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.WarrantyTicketRepository;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetCustomerDeviceWarrantyUseCaseTest {

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    @Mock
    private WarrantyTicketRepository warrantyTicketRepository;

    @InjectMocks
    private GetCustomerDeviceWarrantyUseCase useCase;

    private static final Long CUSTOMER_ID = 5L;
    private static final Long DEVICE_ID = 10L;
    private static final String SERIAL = "LUM-6432-RGB-2026-0001";
    private static final String MAC = "AA:BB:CC:11:22:33";

    private LedInstanceEntity createBoundInstance(Instant boundAt, Instant warrantyExpiresAt, Long ownerId) {
        LedInstanceEntity entity = LedInstanceEntity.createWithState(
                101L,
                SERIAL,
                MAC,
                "REV-A",
                ownerId,
                "Xe Wave Alpha",
                LedInstanceStatus.BOUND,
                Instant.now().minus(30, ChronoUnit.DAYS),
                boundAt,
                warrantyExpiresAt
        );
        try {
            Field idField = LedInstanceEntity.class.getDeclaredField("instanceId");
            idField.setAccessible(true);
            idField.set(entity, DEVICE_ID);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return entity;
    }

    private WarrantyTicketEntity createTicketEntity(
            Long ticketId,
            String serial,
            WarrantyTicketStatus status,
            String desc,
            List<String> evidenceUrls,
            String resolutionNotes,
            Instant createdAt,
            Instant updatedAt
    ) {
        WarrantyTicketEntity ticket = WarrantyTicketEntity.createPending(
                CUSTOMER_ID,
                55L,
                serial,
                desc,
                evidenceUrls
        );
        try {
            Field idField = WarrantyTicketEntity.class.getDeclaredField("ticketId");
            idField.setAccessible(true);
            idField.set(ticket, ticketId);

            Field statusField = WarrantyTicketEntity.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(ticket, status);

            Field resField = WarrantyTicketEntity.class.getDeclaredField("resolutionNotes");
            resField.setAccessible(true);
            resField.set(ticket, resolutionNotes);

            Field createdField = WarrantyTicketEntity.class.getDeclaredField("createdAt");
            createdField.setAccessible(true);
            createdField.set(ticket, createdAt);

            Field updatedField = WarrantyTicketEntity.class.getDeclaredField("updatedAt");
            updatedField.setAccessible(true);
            updatedField.set(ticket, updatedAt);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return ticket;
    }

    @Test
    @DisplayName("execute: when device is active with active ticket, returns warranty overview and active ticket")
    void execute_whenDeviceActiveWithActiveTicket_returnsOverviewAndActiveTicket() {
        Instant now = Instant.now();
        Instant boundAt = now.minus(10, ChronoUnit.DAYS);
        Instant warrantyExpiresAt = now.plus(170, ChronoUnit.DAYS);
        LedInstanceEntity instance = createBoundInstance(boundAt, warrantyExpiresAt, CUSTOMER_ID);

        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(instance));

        WarrantyTicketEntity ticket1 = createTicketEntity(
                101L,
                SERIAL,
                WarrantyTicketStatus.APPROVED,
                "Màn hình LED chớp nháy sau 15 phút.",
                List.of("https://cdn.lumintra.shop/warranty/10/img-01.jpg"),
                "Yêu cầu hợp lệ, vui lòng mang đến cửa hàng.",
                now.minus(2, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS)
        );
        WarrantyTicketEntity ticket2 = createTicketEntity(
                99L,
                SERIAL,
                WarrantyTicketStatus.COMPLETED,
                "Đứt cáp nguồn USB.",
                List.of(),
                "Đã thay cáp mới.",
                now.minus(20, ChronoUnit.DAYS),
                now.minus(18, ChronoUnit.DAYS)
        );

        when(warrantyTicketRepository.findByDeviceSerialNumberOrderByCreatedAtDesc(SERIAL))
                .thenReturn(List.of(ticket1, ticket2));

        CustomerDeviceWarrantyResponse response = useCase.execute(CUSTOMER_ID, DEVICE_ID);

        assertThat(response).isNotNull();
        assertThat(response.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(response.serialNumber()).isEqualTo(SERIAL);
        assertThat(response.deviceName()).isEqualTo("Xe Wave Alpha");
        assertThat(response.boundAt()).isEqualTo(boundAt);
        assertThat(response.warrantyExpiresAt()).isEqualTo(warrantyExpiresAt);
        assertThat(response.warrantyStatus()).isEqualTo(WarrantyStatus.ACTIVE);
        assertThat(response.remainingDays()).isGreaterThanOrEqualTo(169L);

        // Active ticket check
        assertThat(response.activeTicket()).isNotNull();
        assertThat(response.activeTicket().ticketId()).isEqualTo(101L);
        assertThat(response.activeTicket().status()).isEqualTo(WarrantyTicketStatus.APPROVED);
        assertThat(response.activeTicket().evidenceMediaUrls()).containsExactly("https://cdn.lumintra.shop/warranty/10/img-01.jpg");
        assertThat(response.activeTicket().resolutionNotes()).isEqualTo("Yêu cầu hợp lệ, vui lòng mang đến cửa hàng.");

        // Ticket history check
        assertThat(response.ticketHistory()).hasSize(2);
        assertThat(response.ticketHistory().get(0).ticketId()).isEqualTo(101L);
        assertThat(response.ticketHistory().get(1).ticketId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("execute: when device is active without tickets, returns overview with null activeTicket and empty history")
    void execute_whenDeviceActiveWithoutAnyTickets_returnsOverviewWithNullActiveTicketAndEmptyHistory() {
        Instant now = Instant.now();
        Instant boundAt = now.minus(5, ChronoUnit.DAYS);
        Instant warrantyExpiresAt = now.plus(175, ChronoUnit.DAYS);
        LedInstanceEntity instance = createBoundInstance(boundAt, warrantyExpiresAt, CUSTOMER_ID);

        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(instance));
        when(warrantyTicketRepository.findByDeviceSerialNumberOrderByCreatedAtDesc(SERIAL))
                .thenReturn(List.of());

        CustomerDeviceWarrantyResponse response = useCase.execute(CUSTOMER_ID, DEVICE_ID);

        assertThat(response).isNotNull();
        assertThat(response.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(response.warrantyStatus()).isEqualTo(WarrantyStatus.ACTIVE);
        assertThat(response.remainingDays()).isGreaterThanOrEqualTo(174L);
        assertThat(response.activeTicket()).isNull();
        assertThat(response.ticketHistory()).isEmpty();
    }

    @Test
    @DisplayName("execute: when device warranty expired, returns EXPIRED status and zero remaining days")
    void execute_whenDeviceWarrantyExpired_returnsExpiredStatusAndZeroRemainingDays() {
        Instant now = Instant.now();
        Instant boundAt = now.minus(200, ChronoUnit.DAYS);
        Instant warrantyExpiresAt = now.minus(20, ChronoUnit.DAYS);
        LedInstanceEntity instance = createBoundInstance(boundAt, warrantyExpiresAt, CUSTOMER_ID);

        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(instance));
        when(warrantyTicketRepository.findByDeviceSerialNumberOrderByCreatedAtDesc(SERIAL))
                .thenReturn(List.of());

        CustomerDeviceWarrantyResponse response = useCase.execute(CUSTOMER_ID, DEVICE_ID);

        assertThat(response).isNotNull();
        assertThat(response.warrantyStatus()).isEqualTo(WarrantyStatus.EXPIRED);
        assertThat(response.remainingDays()).isEqualTo(0L);
        assertThat(response.activeTicket()).isNull();
        assertThat(response.ticketHistory()).isEmpty();
    }

    @Test
    @DisplayName("execute: when device warranty not activated, returns NOT_ACTIVATED and zero remaining days")
    void execute_whenDeviceWarrantyNotActivated_returnsNotActivatedAndZeroRemainingDays() {
        LedInstanceEntity instance = createBoundInstance(null, null, CUSTOMER_ID);

        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(instance));
        when(warrantyTicketRepository.findByDeviceSerialNumberOrderByCreatedAtDesc(SERIAL))
                .thenReturn(List.of());

        CustomerDeviceWarrantyResponse response = useCase.execute(CUSTOMER_ID, DEVICE_ID);

        assertThat(response).isNotNull();
        assertThat(response.warrantyStatus()).isEqualTo(WarrantyStatus.NOT_ACTIVATED);
        assertThat(response.remainingDays()).isEqualTo(0L);
        assertThat(response.boundAt()).isNull();
        assertThat(response.warrantyExpiresAt()).isNull();
        assertThat(response.activeTicket()).isNull();
        assertThat(response.ticketHistory()).isEmpty();
    }

    @Test
    @DisplayName("execute: when customerId is null, throws InsufficientAuthenticationException")
    void execute_whenCustomerIdIsNull_throwsInsufficientAuthenticationException() {
        assertThatThrownBy(() -> useCase.execute(null, DEVICE_ID))
                .isInstanceOf(InsufficientAuthenticationException.class)
                .hasMessageContaining("Authenticated customer identity is required.");

        verify(ledInstanceRepository, never()).findById(any());
    }

    @Test
    @DisplayName("execute: when deviceId is null, throws LedInstanceNotFoundException")
    void execute_whenDeviceIdIsNull_throwsLedInstanceNotFoundException() {
        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, null))
                .isInstanceOf(LedInstanceNotFoundException.class)
                .hasMessageContaining("LED hardware device not found.");

        verify(ledInstanceRepository, never()).findById(any());
    }

    @Test
    @DisplayName("execute: when device not found in repository, throws LedInstanceNotFoundException")
    void execute_whenDeviceNotFound_throwsLedInstanceNotFoundException() {
        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, DEVICE_ID))
                .isInstanceOf(LedInstanceNotFoundException.class)
                .hasMessageContaining("LED hardware device not found.");

        verify(warrantyTicketRepository, never()).findByDeviceSerialNumberOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("execute: when device is owned by another customer, throws DeviceOwnershipMismatchException")
    void execute_whenDeviceNotOwnedByCustomer_throwsDeviceOwnershipMismatchException() {
        Instant now = Instant.now();
        LedInstanceEntity instance = createBoundInstance(now.minus(10, ChronoUnit.DAYS), now.plus(170, ChronoUnit.DAYS), 999L);
        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(instance));

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, DEVICE_ID))
                .isInstanceOf(DeviceOwnershipMismatchException.class)
                .hasMessageContaining("You do not own this LED hardware device.");

        verify(warrantyTicketRepository, never()).findByDeviceSerialNumberOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("execute: when device has only COMPLETED or REJECTED tickets, returns null activeTicket")
    void execute_whenDeviceHasOnlyCompletedOrRejectedTickets_returnsNullActiveTicket() {
        Instant now = Instant.now();
        Instant boundAt = now.minus(10, ChronoUnit.DAYS);
        Instant warrantyExpiresAt = now.plus(170, ChronoUnit.DAYS);
        LedInstanceEntity instance = createBoundInstance(boundAt, warrantyExpiresAt, CUSTOMER_ID);

        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(instance));

        WarrantyTicketEntity ticket1 = createTicketEntity(
                102L,
                SERIAL,
                WarrantyTicketStatus.REJECTED,
                "Thiết bị rơi nước.",
                List.of(),
                "Từ chối do lỗi người dùng.",
                now.minus(5, ChronoUnit.DAYS),
                now.minus(4, ChronoUnit.DAYS)
        );
        WarrantyTicketEntity ticket2 = createTicketEntity(
                101L,
                SERIAL,
                WarrantyTicketStatus.COMPLETED,
                "Hỏng cổng USB.",
                List.of(),
                "Đã sửa chữa xong.",
                now.minus(15, ChronoUnit.DAYS),
                now.minus(14, ChronoUnit.DAYS)
        );

        when(warrantyTicketRepository.findByDeviceSerialNumberOrderByCreatedAtDesc(SERIAL))
                .thenReturn(List.of(ticket1, ticket2));

        CustomerDeviceWarrantyResponse response = useCase.execute(CUSTOMER_ID, DEVICE_ID);

        assertThat(response).isNotNull();
        assertThat(response.activeTicket()).isNull();
        assertThat(response.ticketHistory()).hasSize(2);
    }

    @Test
    @DisplayName("execute: when device has multiple tickets, preserves history sort order and details")
    void execute_whenDeviceHasMultipleTickets_preservesHistorySortOrder() {
        Instant now = Instant.now();
        Instant boundAt = now.minus(30, ChronoUnit.DAYS);
        Instant warrantyExpiresAt = now.plus(150, ChronoUnit.DAYS);
        LedInstanceEntity instance = createBoundInstance(boundAt, warrantyExpiresAt, CUSTOMER_ID);

        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(instance));

        WarrantyTicketEntity ticket1 = createTicketEntity(
                201L,
                SERIAL,
                WarrantyTicketStatus.PENDING,
                "Ticket mới nhất",
                List.of("https://cdn.lumintra.shop/warranty/10/p1.jpg", "https://cdn.lumintra.shop/warranty/10/p2.jpg"),
                null,
                now.minus(1, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.HOURS)
        );
        WarrantyTicketEntity ticket2 = createTicketEntity(
                200L,
                SERIAL,
                WarrantyTicketStatus.APPROVED,
                "Ticket cũ hơn",
                List.of(),
                "Approved notes",
                now.minus(5, ChronoUnit.DAYS),
                now.minus(4, ChronoUnit.DAYS)
        );

        when(warrantyTicketRepository.findByDeviceSerialNumberOrderByCreatedAtDesc(SERIAL))
                .thenReturn(List.of(ticket1, ticket2));

        CustomerDeviceWarrantyResponse response = useCase.execute(CUSTOMER_ID, DEVICE_ID);

        assertThat(response).isNotNull();
        assertThat(response.activeTicket()).isNotNull();
        assertThat(response.activeTicket().ticketId()).isEqualTo(201L);
        assertThat(response.ticketHistory()).hasSize(2);
        assertThat(response.ticketHistory().get(0).ticketId()).isEqualTo(201L);
        assertThat(response.ticketHistory().get(0).evidenceMediaUrls()).hasSize(2);
        assertThat(response.ticketHistory().get(1).ticketId()).isEqualTo(200L);
    }
}
