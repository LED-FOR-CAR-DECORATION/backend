package com.lumintra.backend.device;

import com.lumintra.backend.device.api.request.WarrantyClaimFilterRequest;
import com.lumintra.backend.device.api.response.ManagedWarrantyClaimResponse;
import com.lumintra.backend.device.application.ListManagedWarrantyClaimsUseCase;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.WarrantyTicketEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.WarrantyTicketRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import com.lumintra.backend.media.domain.service.MediaViewUrlResolver;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListManagedWarrantyClaimsUseCaseTest {

    @Mock
    private WarrantyTicketRepository warrantyTicketRepository;

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    @Mock
    private CustomerLookupService customerLookupService;

    @Spy
    private MediaViewUrlResolver mediaViewUrlResolver = MediaViewUrlResolver.passThrough();

    @InjectMocks
    private ListManagedWarrantyClaimsUseCase useCase;

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

    private LedInstanceEntity createDevice(String serialNumber, String deviceName) {
        try {
            Constructor<LedInstanceEntity> constructor = LedInstanceEntity.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            LedInstanceEntity device = constructor.newInstance();
            device.setSerialNumber(serialNumber);
            device.setDeviceName(deviceName);
            return device;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    @DisplayName("DEV-15 UC-01 [N]: Default filter returns paginated tickets enriched with customer and device metadata")
    void execute_defaultFilter_returnsAllTicketsWithMetadata() {
        WarrantyTicketEntity ticket = createTicket(1L, 10L, "LUM-6432-001", WarrantyTicketStatus.PENDING,
                "Dead pixel column", List.of("https://cdn.lumintra.com/img1.jpg"), null);
        Page<WarrantyTicketEntity> ticketPage = new PageImpl<>(List.of(ticket), Pageable.ofSize(10), 1);

        when(warrantyTicketRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(ticketPage);
        when(customerLookupService.findProfilesByCustomerIds(Set.of(10L)))
                .thenReturn(Map.of(10L, new CustomerProfileSnapshot(10L, "Nguyen Van A", "0901234567", "a@example.com")));
        when(ledInstanceRepository.findBySerialNumberIn(Set.of("LUM-6432-001")))
                .thenReturn(List.of(createDevice("LUM-6432-001", "Living Room LED")));

        Page<ManagedWarrantyClaimResponse> result = useCase.execute(new WarrantyClaimFilterRequest());

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        ManagedWarrantyClaimResponse response = result.getContent().get(0);
        assertThat(response.ticketId()).isEqualTo(1L);
        assertThat(response.customerId()).isEqualTo(10L);
        assertThat(response.customerName()).isEqualTo("Nguyen Van A");
        assertThat(response.customerEmail()).isEqualTo("a@example.com");
        assertThat(response.customerPhone()).isEqualTo("0901234567");
        assertThat(response.deviceSerialNumber()).isEqualTo("LUM-6432-001");
        assertThat(response.deviceName()).isEqualTo("Living Room LED");
        assertThat(response.status()).isEqualTo(WarrantyTicketStatus.PENDING);
        assertThat(response.issueDescription()).isEqualTo("Dead pixel column");
        assertThat(response.evidenceMediaUrls()).containsExactly("https://cdn.lumintra.com/img1.jpg");
        assertThat(response.resolutionNotes()).isNull();
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("DEV-15 UC-02 [N]: Filter by status PENDING correctly delegates to repository with status predicate")
    void execute_filterByStatusPending_delegatesToRepository() {
        WarrantyClaimFilterRequest request = new WarrantyClaimFilterRequest(WarrantyTicketStatus.PENDING, null, 0, 10);
        Page<WarrantyTicketEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(warrantyTicketRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        Page<ManagedWarrantyClaimResponse> result = useCase.execute(request);

        assertThat(result.getContent()).isEmpty();
        verify(warrantyTicketRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("DEV-15 UC-03 [N]: Filter by status APPROVED delegates to repository")
    void execute_filterByStatusApproved_delegatesToRepository() {
        WarrantyClaimFilterRequest request = new WarrantyClaimFilterRequest(WarrantyTicketStatus.APPROVED, null, 0, 10);
        Page<WarrantyTicketEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(warrantyTicketRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        Page<ManagedWarrantyClaimResponse> result = useCase.execute(request);

        assertThat(result.getContent()).isEmpty();
        verify(warrantyTicketRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("DEV-15 UC-04 [N]: Keyword search looks up matched customers and enriches specification")
    void execute_searchKeyword_looksUpCustomersAndAppliesPredicate() {
        WarrantyClaimFilterRequest request = new WarrantyClaimFilterRequest(null, "Nguyen", 0, 10);
        when(customerLookupService.findCustomerIdsByKeyword("Nguyen")).thenReturn(Set.of(10L, 20L));

        WarrantyTicketEntity ticket = createTicket(2L, 10L, "LUM-100", WarrantyTicketStatus.PENDING, "Flickering", List.of(), null);
        when(warrantyTicketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ticket)));
        when(customerLookupService.findProfilesByCustomerIds(Set.of(10L)))
                .thenReturn(Map.of(10L, new CustomerProfileSnapshot(10L, "Nguyen Van A", null, "nguyen@lumintra.com")));
        when(ledInstanceRepository.findBySerialNumberIn(Set.of("LUM-100"))).thenReturn(List.of());

        Page<ManagedWarrantyClaimResponse> result = useCase.execute(request);

        assertThat(result.getContent()).hasSize(1);
        verify(customerLookupService).findCustomerIdsByKeyword("Nguyen");
    }

    @Test
    @DisplayName("DEV-15 UC-05 [N]: Keyword search with no matching customers still queries repository for serial number/issue match")
    void execute_searchKeyword_noCustomersMatched_stillAppliesSpecification() {
        WarrantyClaimFilterRequest request = new WarrantyClaimFilterRequest(null, "LUM-UNKNOWN", 0, 10);
        when(customerLookupService.findCustomerIdsByKeyword("LUM-UNKNOWN")).thenReturn(Collections.emptySet());
        when(warrantyTicketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        Page<ManagedWarrantyClaimResponse> result = useCase.execute(request);

        assertThat(result.getContent()).isEmpty();
        verify(customerLookupService).findCustomerIdsByKeyword("LUM-UNKNOWN");
        verify(warrantyTicketRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("DEV-15 UC-06 [B]: Empty repository result returns empty page without customer/device enrichment queries")
    void execute_emptyResult_returnsEmptyPageWithoutEnrichmentLookups() {
        when(warrantyTicketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        Page<ManagedWarrantyClaimResponse> result = useCase.execute(new WarrantyClaimFilterRequest());

        assertThat(result.isEmpty()).isTrue();
        verify(customerLookupService, never()).findProfilesByCustomerIds(anyCollection());
        verify(ledInstanceRepository, never()).findBySerialNumberIn(anyCollection());
    }

    @Test
    @DisplayName("DEV-15 UC-07 [A]: Safe enrichment when customer or device is not found in database")
    void execute_nullCustomerOrDevice_safelyEnrichesWithNulls() {
        WarrantyTicketEntity ticket = createTicket(3L, 999L, "UNKNOWN-SERIAL", WarrantyTicketStatus.REJECTED,
                "Physical damage", List.of(), "Water damage detected");
        when(warrantyTicketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ticket)));
        when(customerLookupService.findProfilesByCustomerIds(Set.of(999L))).thenReturn(Collections.emptyMap());
        when(ledInstanceRepository.findBySerialNumberIn(Set.of("UNKNOWN-SERIAL"))).thenReturn(Collections.emptyList());

        Page<ManagedWarrantyClaimResponse> result = useCase.execute(new WarrantyClaimFilterRequest());

        assertThat(result.getContent()).hasSize(1);
        ManagedWarrantyClaimResponse item = result.getContent().get(0);
        assertThat(item.customerId()).isEqualTo(999L);
        assertThat(item.customerName()).isNull();
        assertThat(item.customerEmail()).isNull();
        assertThat(item.customerPhone()).isNull();
        assertThat(item.deviceSerialNumber()).isEqualTo("UNKNOWN-SERIAL");
        assertThat(item.deviceName()).isNull();
        assertThat(item.status()).isEqualTo(WarrantyTicketStatus.REJECTED);
        assertThat(item.resolutionNotes()).isEqualTo("Water damage detected");
    }

    @Test
    @DisplayName("DEV-15 UC-08 [N]: Null filter defaults safely to page 0, size 10, without query")
    void execute_nullFilter_defaultsToPageZeroSizeTen() {
        when(warrantyTicketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        Page<ManagedWarrantyClaimResponse> result = useCase.execute(null);

        assertThat(result.isEmpty()).isTrue();
        verify(customerLookupService, never()).findCustomerIdsByKeyword(any());
    }

    @Test
    @DisplayName("DEV-15 UC-09 [N]: Multiple claims with same customer and device deduplicate lookup calls")
    void execute_multipleClaimsSameCustomerAndDevice_batchFetchesWithoutDuplicates() {
        WarrantyTicketEntity ticket1 = createTicket(10L, 10L, "LUM-100", WarrantyTicketStatus.PENDING, "Issue 1", List.of(), null);
        WarrantyTicketEntity ticket2 = createTicket(11L, 10L, "LUM-100", WarrantyTicketStatus.COMPLETED, "Issue 2", List.of(), "Fixed");
        when(warrantyTicketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ticket1, ticket2)));
        when(customerLookupService.findProfilesByCustomerIds(Set.of(10L)))
                .thenReturn(Map.of(10L, new CustomerProfileSnapshot(10L, "Customer A", "0123456789", "a@lumintra.com")));
        when(ledInstanceRepository.findBySerialNumberIn(Set.of("LUM-100")))
                .thenReturn(List.of(createDevice("LUM-100", "Device A")));

        Page<ManagedWarrantyClaimResponse> result = useCase.execute(new WarrantyClaimFilterRequest());

        assertThat(result.getContent()).hasSize(2);
        verify(customerLookupService).findProfilesByCustomerIds(Set.of(10L));
        verify(ledInstanceRepository).findBySerialNumberIn(Set.of("LUM-100"));
    }

    @Test
    @DisplayName("DEV-15 UC-10 [N]: Pagination parameters are correctly mapped with createdAt DESC sort")
    void execute_paginationParameters_passesCorrectPageAndSizeAndSort() {
        WarrantyClaimFilterRequest request = new WarrantyClaimFilterRequest(null, null, 2, 5);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(warrantyTicketRepository.findAll(any(Specification.class), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        useCase.execute(request);

        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(2);
        assertThat(captured.getPageSize()).isEqualTo(5);
        assertThat(captured.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(captured.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
