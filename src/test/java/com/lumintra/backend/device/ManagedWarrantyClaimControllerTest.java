package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.ManagedWarrantyClaimController;
import com.lumintra.backend.device.api.request.WarrantyClaimFilterRequest;
import com.lumintra.backend.device.api.response.ManagedWarrantyClaimResponse;
import com.lumintra.backend.device.application.ListManagedWarrantyClaimsUseCase;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ManagedWarrantyClaimController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ManagedWarrantyClaimControllerTest {

    private static final String PATH = "/api/management/manager/warranty-claims";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListManagedWarrantyClaimsUseCase listManagedWarrantyClaimsUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private ManagedWarrantyClaimResponse sampleClaimResponse() {
        return new ManagedWarrantyClaimResponse(
                1L,
                5L,
                "Nguyen Van A",
                "customer@lumintra.com",
                "0987654321",
                "LUM-6432-RGB-2026-0001",
                "Living Room LED",
                12L,
                WarrantyTicketStatus.PENDING,
                "Màn hình LED bị chớp nháy sau 15 phút.",
                List.of("https://cdn.lumintra.shop/warranty/10/evidence-01.jpg"),
                null,
                Instant.parse("2026-11-16T15:40:00Z"),
                null
        );
    }

    private Page<ManagedWarrantyClaimResponse> samplePage() {
        return new PageImpl<>(List.of(sampleClaimResponse()), PageRequest.of(0, 10), 1);
    }

    @Test
    @DisplayName("DEV-15 API-01 [N]: MANAGER role receives 200 OK with paginated warranty claims and metadata")
    void listWarrantyClaims_managerRole_returns200WithPaginatedClaims() throws Exception {
        when(listManagedWarrantyClaimsUseCase.execute(any())).thenReturn(samplePage());

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].ticketId").value(1))
                .andExpect(jsonPath("$.data[0].customerId").value(5))
                .andExpect(jsonPath("$.data[0].customerName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data[0].customerEmail").value("customer@lumintra.com"))
                .andExpect(jsonPath("$.data[0].customerPhone").value("0987654321"))
                .andExpect(jsonPath("$.data[0].deviceSerialNumber").value("LUM-6432-RGB-2026-0001"))
                .andExpect(jsonPath("$.data[0].deviceName").value("Living Room LED"))
                .andExpect(jsonPath("$.data[0].orderItemId").value(12))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].issueDescription").value("Màn hình LED bị chớp nháy sau 15 phút."))
                .andExpect(jsonPath("$.data[0].evidenceMediaUrls[0]").value("https://cdn.lumintra.shop/warranty/10/evidence-01.jpg"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(10))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(1));
    }

    @Test
    @DisplayName("DEV-15 API-02 [N]: ADMIN role receives 200 OK with paginated warranty claims")
    void listWarrantyClaims_adminRole_returns200WithPaginatedClaims() throws Exception {
        when(listManagedWarrantyClaimsUseCase.execute(any())).thenReturn(samplePage());

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("2").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ticketId").value(1))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("DEV-15 API-03 [N]: Status filter parameter is passed to use case")
    void listWarrantyClaims_withStatusFilter_passesFilterToUseCase() throws Exception {
        when(listManagedWarrantyClaimsUseCase.execute(any())).thenReturn(samplePage());

        mockMvc.perform(get(PATH)
                        .param("status", "PENDING")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER")))
                .andExpect(status().isOk());

        ArgumentCaptor<WarrantyClaimFilterRequest> captor = ArgumentCaptor.forClass(WarrantyClaimFilterRequest.class);
        verify(listManagedWarrantyClaimsUseCase).execute(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(WarrantyTicketStatus.PENDING);
    }

    @Test
    @DisplayName("DEV-15 API-04 [N]: Keyword search query parameter is passed to use case")
    void listWarrantyClaims_withKeywordSearch_passesQueryToUseCase() throws Exception {
        when(listManagedWarrantyClaimsUseCase.execute(any())).thenReturn(samplePage());

        mockMvc.perform(get(PATH)
                        .param("query", "LUM-6432")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER")))
                .andExpect(status().isOk());

        ArgumentCaptor<WarrantyClaimFilterRequest> captor = ArgumentCaptor.forClass(WarrantyClaimFilterRequest.class);
        verify(listManagedWarrantyClaimsUseCase).execute(captor.capture());
        assertThat(captor.getValue().query()).isEqualTo("LUM-6432");
    }

    @Test
    @DisplayName("DEV-15 API-05 [N]: Custom pagination parameters page and size are passed correctly")
    void listWarrantyClaims_withPagination_passesPageAndSize() throws Exception {
        when(listManagedWarrantyClaimsUseCase.execute(any())).thenReturn(samplePage());

        mockMvc.perform(get(PATH)
                        .param("page", "2")
                        .param("size", "25")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER")))
                .andExpect(status().isOk());

        ArgumentCaptor<WarrantyClaimFilterRequest> captor = ArgumentCaptor.forClass(WarrantyClaimFilterRequest.class);
        verify(listManagedWarrantyClaimsUseCase).execute(captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(2);
        assertThat(captor.getValue().size()).isEqualTo(25);
    }

    @Test
    @DisplayName("DEV-15 API-06 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void listWarrantyClaims_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(listManagedWarrantyClaimsUseCase);
    }

    @Test
    @DisplayName("DEV-15 API-07 [A]: CUSTOMER role returns 403 FORBIDDEN")
    void listWarrantyClaims_customerRole_returns403() throws Exception {
        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("10").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(listManagedWarrantyClaimsUseCase);
    }

    @Test
    @DisplayName("DEV-15 API-08 [A]: STAFF role returns 403 FORBIDDEN")
    void listWarrantyClaims_staffRole_returns403() throws Exception {
        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("20").roles("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(listManagedWarrantyClaimsUseCase);
    }

    @Test
    @DisplayName("DEV-15 API-09 [A]: DESIGNER role returns 403 FORBIDDEN")
    void listWarrantyClaims_designerRole_returns403() throws Exception {
        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("30").roles("DESIGNER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(listManagedWarrantyClaimsUseCase);
    }

    @Test
    @DisplayName("DEV-15 API-10 [B]: Negative page parameter returns 400 VALIDATION_FAILED")
    void listWarrantyClaims_invalidPagination_negativePage_returns400() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("page", "-1")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listManagedWarrantyClaimsUseCase);
    }
}
