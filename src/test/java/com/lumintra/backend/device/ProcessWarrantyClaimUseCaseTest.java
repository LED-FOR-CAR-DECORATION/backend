package com.lumintra.backend.device;

import com.lumintra.backend.device.api.request.ProcessWarrantyClaimRequest;
import com.lumintra.backend.device.api.response.WarrantyClaimResponse;
import com.lumintra.backend.device.application.ProcessWarrantyClaimUseCase;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.WarrantyTicketEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.WarrantyTicketRepository;
import com.lumintra.backend.shared.exception.device.InvalidWarrantyTransitionException;
import com.lumintra.backend.shared.exception.device.WarrantyClaimNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.lumintra.backend.media.domain.service.MediaViewUrlResolver;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessWarrantyClaimUseCaseTest {

    @Mock
    private WarrantyTicketRepository warrantyTicketRepository;

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    @Spy
    private MediaViewUrlResolver mediaViewUrlResolver = MediaViewUrlResolver.passThrough();

    @InjectMocks
    private ProcessWarrantyClaimUseCase useCase;

    private static final Instant NOW = Instant.parse("2026-11-16T15:00:00Z");

    private WarrantyTicketEntity createTicket(
            Long ticketId,
            Long customerId,
            String serial,
            WarrantyTicketStatus status,
            String description,
            List<String> mediaUrls,
            String resolutionNotes) {
        WarrantyTicketEntity ticket = WarrantyTicketEntity.createPending(
                customerId,
                100L,
                serial,
                description,
                mediaUrls
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
            createdField.set(ticket, NOW);

            Field updatedField = WarrantyTicketEntity.class.getDeclaredField("updatedAt");
            updatedField.setAccessible(true);
            updatedField.set(ticket, NOW.plusSeconds(3600));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return ticket;
    }

    private LedInstanceEntity createDevice(Long instanceId, String serialNumber, String deviceName) {
        try {
            Constructor<LedInstanceEntity> constructor = LedInstanceEntity.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            LedInstanceEntity device = constructor.newInstance();
            device.setInstanceId(instanceId);
            device.setSerialNumber(serialNumber);
            device.setDeviceName(deviceName);
            return device;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    @DisplayName("DEV-16 UC-01 [N]: PENDING claim transitions to APPROVED with resolution notes")
    void execute_pendingToApproved_updatesStatusAndNotesSuccessfully() {
        WarrantyTicketEntity ticket = createTicket(1L, 10L, "LUM-6432-001", WarrantyTicketStatus.PENDING,
                "Dead pixel column", List.of("https://cdn.lumintra.com/img1.jpg"), null);
        when(warrantyTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(warrantyTicketRepository.save(any(WarrantyTicketEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledInstanceRepository.findBySerialNumber("LUM-6432-001"))
                .thenReturn(Optional.of(createDevice(10L, "LUM-6432-001", "Living Room LED")));

        ProcessWarrantyClaimRequest request = new ProcessWarrantyClaimRequest(
                WarrantyTicketStatus.APPROVED,
                "Yêu cầu hợp lệ, mời khách mang thiết bị đến cửa hàng."
        );

        WarrantyClaimResponse response = useCase.execute(1L, request);

        assertThat(response.ticketId()).isEqualTo(1L);
        assertThat(response.deviceId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(WarrantyTicketStatus.APPROVED);
        assertThat(response.resolutionNotes()).isEqualTo("Yêu cầu hợp lệ, mời khách mang thiết bị đến cửa hàng.");
        verify(warrantyTicketRepository).save(ticket);
    }

    @Test
    @DisplayName("DEV-16 UC-02 [N]: PENDING claim transitions to APPROVED without resolution notes (optional for APPROVED)")
    void execute_pendingToApproved_nullNotes_allowed() {
        WarrantyTicketEntity ticket = createTicket(1L, 10L, "LUM-6432-001", WarrantyTicketStatus.PENDING,
                "Dead pixel column", List.of(), null);
        when(warrantyTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(warrantyTicketRepository.save(any(WarrantyTicketEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledInstanceRepository.findBySerialNumber("LUM-6432-001")).thenReturn(Optional.empty());

        ProcessWarrantyClaimRequest request = new ProcessWarrantyClaimRequest(WarrantyTicketStatus.APPROVED, null);

        WarrantyClaimResponse response = useCase.execute(1L, request);

        assertThat(response.status()).isEqualTo(WarrantyTicketStatus.APPROVED);
        assertThat(response.resolutionNotes()).isNull();
    }

    @Test
    @DisplayName("DEV-16 UC-03 [N]: PENDING claim transitions to REJECTED with mandatory reason")
    void execute_pendingToRejected_withNotes_updatesStatusSuccessfully() {
        WarrantyTicketEntity ticket = createTicket(2L, 10L, "LUM-6432-002", WarrantyTicketStatus.PENDING,
                "Physical cracked screen", List.of(), null);
        when(warrantyTicketRepository.findById(2L)).thenReturn(Optional.of(ticket));
        when(warrantyTicketRepository.save(any(WarrantyTicketEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledInstanceRepository.findBySerialNumber("LUM-6432-002")).thenReturn(Optional.empty());

        ProcessWarrantyClaimRequest request = new ProcessWarrantyClaimRequest(
                WarrantyTicketStatus.REJECTED,
                "Từ chối do màn hình bị vỡ nứt do ngoại lực theo BR-FL5-02."
        );

        WarrantyClaimResponse response = useCase.execute(2L, request);

        assertThat(response.status()).isEqualTo(WarrantyTicketStatus.REJECTED);
        assertThat(response.resolutionNotes()).contains("vỡ nứt");
    }

    @Test
    @DisplayName("DEV-16 UC-04 [N]: APPROVED claim transitions to COMPLETED after servicing")
    void execute_approvedToCompleted_withNotes_updatesStatusSuccessfully() {
        WarrantyTicketEntity ticket = createTicket(3L, 10L, "LUM-6432-003", WarrantyTicketStatus.APPROVED,
                "Loose connection", List.of(), "Waiting for unit");
        when(warrantyTicketRepository.findById(3L)).thenReturn(Optional.of(ticket));
        when(warrantyTicketRepository.save(any(WarrantyTicketEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledInstanceRepository.findBySerialNumber("LUM-6432-003"))
                .thenReturn(Optional.of(createDevice(15L, "LUM-6432-003", "Main Hall LED")));

        ProcessWarrantyClaimRequest request = new ProcessWarrantyClaimRequest(
                WarrantyTicketStatus.COMPLETED,
                "Đã thay thế cáp ribbon và kiểm tra nguồn 5V ổn định."
        );

        WarrantyClaimResponse response = useCase.execute(3L, request);

        assertThat(response.status()).isEqualTo(WarrantyTicketStatus.COMPLETED);
        assertThat(response.resolutionNotes()).contains("Đã thay thế cáp ribbon");
    }

    @Test
    @DisplayName("DEV-16 UC-05 [N]: APPROVED claim transitions to REJECTED if inspection reveals hardware tampering per BR-FL5-05")
    void execute_approvedToRejected_withNotes_updatesStatusSuccessfully() {
        WarrantyTicketEntity ticket = createTicket(4L, 10L, "LUM-6432-004", WarrantyTicketStatus.APPROVED,
                "Burned LED row", List.of(), "Bring device to store");
        when(warrantyTicketRepository.findById(4L)).thenReturn(Optional.of(ticket));
        when(warrantyTicketRepository.save(any(WarrantyTicketEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProcessWarrantyClaimRequest request = new ProcessWarrantyClaimRequest(
                WarrantyTicketStatus.REJECTED,
                "Phát hiện dấu hiệu hàn can thiệp mạch và nạp firmware không chính thức per BR-FL5-05."
        );

        WarrantyClaimResponse response = useCase.execute(4L, request);

        assertThat(response.status()).isEqualTo(WarrantyTicketStatus.REJECTED);
        assertThat(response.resolutionNotes()).contains("can thiệp mạch");
    }

    @Test
    @DisplayName("DEV-16 UC-06 [A]: Direct transition from PENDING to COMPLETED is forbidden")
    void execute_pendingToCompleted_disallowed_throwsInvalidWarrantyTransitionException() {
        WarrantyTicketEntity ticket = createTicket(5L, 10L, "LUM-6432-005", WarrantyTicketStatus.PENDING,
                "Issue", List.of(), null);
        when(warrantyTicketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        ProcessWarrantyClaimRequest request = new ProcessWarrantyClaimRequest(
                WarrantyTicketStatus.COMPLETED,
                "Đã sửa xong trực tiếp"
        );

        assertThatThrownBy(() -> useCase.execute(5L, request))
                .isInstanceOf(InvalidWarrantyTransitionException.class)
                .hasMessageContaining("Cannot transition warranty claim from PENDING to COMPLETED");

        verify(warrantyTicketRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEV-16 UC-07 [A]: Terminal COMPLETED state cannot transition to any status")
    void execute_terminalCompleted_cannotTransition_throwsInvalidWarrantyTransitionException() {
        WarrantyTicketEntity ticket = createTicket(6L, 10L, "LUM-6432-006", WarrantyTicketStatus.COMPLETED,
                "Resolved defect", List.of(), "Fixed");
        when(warrantyTicketRepository.findById(6L)).thenReturn(Optional.of(ticket));

        ProcessWarrantyClaimRequest request = new ProcessWarrantyClaimRequest(
                WarrantyTicketStatus.APPROVED,
                "Reopening"
        );

        assertThatThrownBy(() -> useCase.execute(6L, request))
                .isInstanceOf(InvalidWarrantyTransitionException.class)
                .hasMessageContaining("Cannot transition warranty claim from COMPLETED to APPROVED");

        verify(warrantyTicketRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEV-16 UC-08 [A]: Terminal REJECTED state cannot transition to any status")
    void execute_terminalRejected_cannotTransition_throwsInvalidWarrantyTransitionException() {
        WarrantyTicketEntity ticket = createTicket(7L, 10L, "LUM-6432-007", WarrantyTicketStatus.REJECTED,
                "Tampered unit", List.of(), "Tampered");
        when(warrantyTicketRepository.findById(7L)).thenReturn(Optional.of(ticket));

        ProcessWarrantyClaimRequest request = new ProcessWarrantyClaimRequest(
                WarrantyTicketStatus.APPROVED,
                "Overriding rejection"
        );

        assertThatThrownBy(() -> useCase.execute(7L, request))
                .isInstanceOf(InvalidWarrantyTransitionException.class)
                .hasMessageContaining("Cannot transition warranty claim from REJECTED to APPROVED");

        verify(warrantyTicketRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEV-16 UC-09 [A]: Transitioning to REJECTED or COMPLETED without resolutionNotes throws IllegalArgumentException")
    void execute_rejectedOrCompletedWithoutNotes_throwsIllegalArgumentException() {
        WarrantyTicketEntity ticket = createTicket(8L, 10L, "LUM-6432-008", WarrantyTicketStatus.PENDING,
                "Defect", List.of(), null);
        when(warrantyTicketRepository.findById(8L)).thenReturn(Optional.of(ticket));

        ProcessWarrantyClaimRequest request = new ProcessWarrantyClaimRequest(WarrantyTicketStatus.REJECTED, null);

        assertThatThrownBy(() -> useCase.execute(8L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolutionNotes is required when transitioning to REJECTED");
    }

    @Test
    @DisplayName("DEV-16 UC-10 [A]: Unknown claim ID throws WarrantyClaimNotFoundException")
    void execute_claimNotFound_throwsWarrantyClaimNotFoundException() {
        when(warrantyTicketRepository.findById(999L)).thenReturn(Optional.empty());

        ProcessWarrantyClaimRequest request = new ProcessWarrantyClaimRequest(WarrantyTicketStatus.APPROVED, "Notes");

        assertThatThrownBy(() -> useCase.execute(999L, request))
                .isInstanceOf(WarrantyClaimNotFoundException.class)
                .hasMessageContaining("Warranty claim with ID 999 not found.");
    }

    @Test
    @DisplayName("DEV-16 UC-11 [B]: Non-positive or null claim ID throws WarrantyClaimNotFoundException")
    void execute_invalidClaimId_throwsWarrantyClaimNotFoundException() {
        ProcessWarrantyClaimRequest request = new ProcessWarrantyClaimRequest(WarrantyTicketStatus.APPROVED, "Notes");

        assertThatThrownBy(() -> useCase.execute(0L, request))
                .isInstanceOf(WarrantyClaimNotFoundException.class)
                .hasMessageContaining("Warranty claim not found.");

        assertThatThrownBy(() -> useCase.execute(null, request))
                .isInstanceOf(WarrantyClaimNotFoundException.class)
                .hasMessageContaining("Warranty claim not found.");
    }
}
