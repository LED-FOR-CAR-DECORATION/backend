package com.lumintra.backend.device;

import com.lumintra.backend.device.api.request.SubmitWarrantyClaimRequest;
import com.lumintra.backend.device.api.response.WarrantyClaimResponse;
import com.lumintra.backend.device.application.SubmitWarrantyClaimUseCase;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.WarrantyTicketEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.WarrantyTicketRepository;
import com.lumintra.backend.media.domain.model.MediaPurpose;
import com.lumintra.backend.media.domain.service.PrivateMediaReferenceValidator;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import com.lumintra.backend.shared.exception.device.WarrantyClaimConflictException;
import com.lumintra.backend.shared.exception.device.WarrantyExpiredException;
import com.lumintra.backend.shared.exception.device.WarrantyNotActiveException;
import com.lumintra.backend.shared.exception.media.InvalidMediaUrlException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.lumintra.backend.media.domain.service.MediaViewUrlResolver;
import org.mockito.InjectMocks;
import org.mockito.Spy;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmitWarrantyClaimUseCaseTest {

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    @Mock
    private WarrantyTicketRepository warrantyTicketRepository;

    @Mock
    private OrderFulfillmentLookupService orderFulfillmentLookupService;

    @Mock
    private PrivateMediaReferenceValidator mediaReferenceValidator;

    @Spy
    private MediaViewUrlResolver mediaViewUrlResolver = MediaViewUrlResolver.passThrough();

    @InjectMocks
    private SubmitWarrantyClaimUseCase useCase;

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

    @Test
    @DisplayName("execute: when request is valid with evidence URLs, creates PENDING ticket and returns response")
    void execute_whenValidRequestWithEvidenceUrls_createsPendingTicketAndReturnsResponse() {
        Instant now = Instant.now();
        Instant boundAt = now.minus(10, ChronoUnit.DAYS);
        Instant warrantyExpiresAt = now.plus(170, ChronoUnit.DAYS);
        LedInstanceEntity instance = createBoundInstance(boundAt, warrantyExpiresAt, CUSTOMER_ID);

        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(instance));
        when(warrantyTicketRepository.existsByDeviceSerialNumberAndStatusIn(eq(SERIAL), any())).thenReturn(false);
        when(orderFulfillmentLookupService.findFulfillmentByInstanceId(DEVICE_ID))
                .thenReturn(Optional.of(new InstanceFulfillmentSnapshot(DEVICE_ID, 20L, 55L, CUSTOMER_ID, "DELIVERED")));

        List<String> evidenceUrls = List.of(
                "warranty/10/123e4567-e89b-12d3-a456-426614174000-evidence-01.jpg",
                "warranty/10/223e4567-e89b-12d3-a456-426614174000-evidence-02.jpg"
        );

        WarrantyTicketEntity savedTicket = WarrantyTicketEntity.createPending(
                CUSTOMER_ID,
                55L,
                SERIAL,
                "Màn hình LED bị chớp nháy sau 15 phút.",
                evidenceUrls
        );
        try {
            Field idField = WarrantyTicketEntity.class.getDeclaredField("ticketId");
            idField.setAccessible(true);
            idField.set(savedTicket, 1001L);
            Field createdField = WarrantyTicketEntity.class.getDeclaredField("createdAt");
            createdField.setAccessible(true);
            createdField.set(savedTicket, now);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        when(warrantyTicketRepository.save(any(WarrantyTicketEntity.class))).thenReturn(savedTicket);

        SubmitWarrantyClaimRequest request = new SubmitWarrantyClaimRequest(
                "Màn hình LED bị chớp nháy sau 15 phút.",
                evidenceUrls
        );

        when(mediaReferenceValidator.requireOwnedObjectKey(any(), eq(MediaPurpose.WARRANTY_EVIDENCE), eq(DEVICE_ID)))
                .thenAnswer(inv -> inv.getArgument(0));

        WarrantyClaimResponse response = useCase.execute(CUSTOMER_ID, DEVICE_ID, request);

        assertThat(response).isNotNull();
        assertThat(response.ticketId()).isEqualTo(1001L);
        assertThat(response.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(response.deviceSerialNumber()).isEqualTo(SERIAL);
        assertThat(response.deviceName()).isEqualTo("Xe Wave Alpha");
        assertThat(response.status()).isEqualTo(WarrantyTicketStatus.PENDING);
        assertThat(response.issueDescription()).isEqualTo("Màn hình LED bị chớp nháy sau 15 phút.");
        assertThat(response.evidenceMediaUrls()).containsExactlyElementsOf(evidenceUrls);
        assertThat(response.resolutionNotes()).isNull();

        verify(mediaReferenceValidator).requireOwnedObjectKey(evidenceUrls.get(0), MediaPurpose.WARRANTY_EVIDENCE, DEVICE_ID);
        verify(mediaReferenceValidator).requireOwnedObjectKey(evidenceUrls.get(1), MediaPurpose.WARRANTY_EVIDENCE, DEVICE_ID);
        verify(warrantyTicketRepository).save(any(WarrantyTicketEntity.class));
    }

    @Test
    @DisplayName("execute: when request is valid without evidence URLs, creates PENDING ticket successfully")
    void execute_whenValidRequestWithoutEvidenceUrls_createsPendingTicket() {
        Instant now = Instant.now();
        Instant boundAt = now.minus(5, ChronoUnit.DAYS);
        Instant warrantyExpiresAt = now.plus(175, ChronoUnit.DAYS);
        LedInstanceEntity instance = createBoundInstance(boundAt, warrantyExpiresAt, CUSTOMER_ID);

        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(instance));
        when(warrantyTicketRepository.existsByDeviceSerialNumberAndStatusIn(eq(SERIAL), any())).thenReturn(false);
        when(orderFulfillmentLookupService.findFulfillmentByInstanceId(DEVICE_ID)).thenReturn(Optional.empty());

        WarrantyTicketEntity savedTicket = WarrantyTicketEntity.createPending(
                CUSTOMER_ID,
                null,
                SERIAL,
                "Màn hình LED không lên nguồn khi cắm tẩu sạc.",
                null
        );
        try {
            Field idField = WarrantyTicketEntity.class.getDeclaredField("ticketId");
            idField.setAccessible(true);
            idField.set(savedTicket, 1002L);
            Field createdField = WarrantyTicketEntity.class.getDeclaredField("createdAt");
            createdField.setAccessible(true);
            createdField.set(savedTicket, now);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        when(warrantyTicketRepository.save(any(WarrantyTicketEntity.class))).thenReturn(savedTicket);

        SubmitWarrantyClaimRequest request = new SubmitWarrantyClaimRequest(
                "Màn hình LED không lên nguồn khi cắm tẩu sạc.",
                null
        );

        WarrantyClaimResponse response = useCase.execute(CUSTOMER_ID, DEVICE_ID, request);

        assertThat(response).isNotNull();
        assertThat(response.ticketId()).isEqualTo(1002L);
        assertThat(response.status()).isEqualTo(WarrantyTicketStatus.PENDING);
        assertThat(response.evidenceMediaUrls()).isEmpty();

        verify(mediaReferenceValidator, never()).requireOwnedObjectKey(any(), any(), any());
        verify(warrantyTicketRepository).save(any(WarrantyTicketEntity.class));
    }

    @Test
    @DisplayName("execute: when customerId is null, throws InsufficientAuthenticationException")
    void execute_whenNullCustomerId_throwsInsufficientAuthenticationException() {
        SubmitWarrantyClaimRequest request = new SubmitWarrantyClaimRequest(
                "Màn hình hỏng",
                null
        );

        assertThatThrownBy(() -> useCase.execute(null, DEVICE_ID, request))
                .isInstanceOf(InsufficientAuthenticationException.class)
                .hasMessageContaining("Authenticated customer identity is required.");
    }

    @Test
    @DisplayName("execute: when deviceId is null, throws LedInstanceNotFoundException")
    void execute_whenNullDeviceId_throwsLedInstanceNotFoundException() {
        SubmitWarrantyClaimRequest request = new SubmitWarrantyClaimRequest(
                "Màn hình hỏng",
                null
        );

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, null, request))
                .isInstanceOf(LedInstanceNotFoundException.class)
                .hasMessageContaining("LED hardware device not found.");
    }

    @Test
    @DisplayName("execute: when device not found, throws LedInstanceNotFoundException")
    void execute_whenDeviceNotFound_throwsLedInstanceNotFoundException() {
        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());

        SubmitWarrantyClaimRequest request = new SubmitWarrantyClaimRequest(
                "Màn hình LED bị chớp nháy sau 15 phút.",
                null
        );

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, DEVICE_ID, request))
                .isInstanceOf(LedInstanceNotFoundException.class)
                .hasMessageContaining("LED hardware device not found.");
    }

    @Test
    @DisplayName("execute: when device is owned by another customer, throws DeviceOwnershipMismatchException")
    void execute_whenDeviceNotOwnedByCustomer_throwsDeviceOwnershipMismatchException() {
        Instant now = Instant.now();
        LedInstanceEntity instance = createBoundInstance(now.minus(10, ChronoUnit.DAYS), now.plus(170, ChronoUnit.DAYS), 999L);
        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(instance));

        SubmitWarrantyClaimRequest request = new SubmitWarrantyClaimRequest(
                "Màn hình LED bị chớp nháy sau 15 phút.",
                null
        );

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, DEVICE_ID, request))
                .isInstanceOf(DeviceOwnershipMismatchException.class)
                .hasMessageContaining("You do not own this LED hardware device.");
    }

    @Test
    @DisplayName("execute: when warranty has not been activated, throws WarrantyNotActiveException")
    void execute_whenWarrantyNotActivated_throwsWarrantyNotActiveException() {
        LedInstanceEntity instance = createBoundInstance(null, null, CUSTOMER_ID);
        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(instance));

        SubmitWarrantyClaimRequest request = new SubmitWarrantyClaimRequest(
                "Màn hình LED bị chớp nháy sau 15 phút.",
                null
        );

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, DEVICE_ID, request))
                .isInstanceOf(WarrantyNotActiveException.class)
                .hasMessageContaining("Warranty for this device has not been activated.");
    }

    @Test
    @DisplayName("execute: when warranty has expired, throws WarrantyExpiredException")
    void execute_whenWarrantyExpired_throwsWarrantyExpiredException() {
        Instant now = Instant.now();
        Instant boundAt = now.minus(200, ChronoUnit.DAYS);
        Instant warrantyExpiresAt = now.minus(20, ChronoUnit.DAYS);
        LedInstanceEntity instance = createBoundInstance(boundAt, warrantyExpiresAt, CUSTOMER_ID);

        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(instance));

        SubmitWarrantyClaimRequest request = new SubmitWarrantyClaimRequest(
                "Màn hình LED bị chớp nháy sau 15 phút.",
                null
        );

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, DEVICE_ID, request))
                .isInstanceOf(WarrantyExpiredException.class)
                .hasMessageContaining("Warranty for this device has expired.");
    }

    @Test
    @DisplayName("execute: when active claim already exists (PENDING/APPROVED), throws WarrantyClaimConflictException")
    void execute_whenActiveClaimAlreadyExists_throwsWarrantyClaimConflictException() {
        Instant now = Instant.now();
        Instant boundAt = now.minus(10, ChronoUnit.DAYS);
        Instant warrantyExpiresAt = now.plus(170, ChronoUnit.DAYS);
        LedInstanceEntity instance = createBoundInstance(boundAt, warrantyExpiresAt, CUSTOMER_ID);

        when(ledInstanceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(instance));
        when(warrantyTicketRepository.existsByDeviceSerialNumberAndStatusIn(eq(SERIAL), any())).thenReturn(true);

        SubmitWarrantyClaimRequest request = new SubmitWarrantyClaimRequest(
                "Màn hình LED bị chớp nháy sau 15 phút.",
                null
        );

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, DEVICE_ID, request))
                .isInstanceOf(WarrantyClaimConflictException.class)
                .hasMessageContaining("An active warranty claim already exists for this device.");

        verify(warrantyTicketRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute: when evidence media URL is invalid, throws InvalidMediaUrlException")
    void execute_whenEvidenceUrlInvalid_throwsInvalidMediaUrlException() {
        doThrow(new InvalidMediaUrlException("Invalid URL"))
                .when(mediaReferenceValidator).requireOwnedObjectKey(eq("https://malicious.site/hack.jpg"), eq(MediaPurpose.WARRANTY_EVIDENCE), any());

        SubmitWarrantyClaimRequest request = new SubmitWarrantyClaimRequest(
                "Màn hình LED bị chớp nháy sau 15 phút.",
                List.of("https://malicious.site/hack.jpg")
        );

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, DEVICE_ID, request))
                .isInstanceOf(InvalidMediaUrlException.class);

        verify(ledInstanceRepository, never()).findById(any());
    }
}
