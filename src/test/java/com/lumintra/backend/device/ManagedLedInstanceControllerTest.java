package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.ManagedLedInstanceController;
import com.lumintra.backend.device.api.request.LedInstanceFilterRequest;
import com.lumintra.backend.device.api.response.LedInstanceListResponse;
import com.lumintra.backend.device.application.ListLedInstancesUseCase;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ManagedLedInstanceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ManagedLedInstanceControllerTest {

    private static final String PATH = "/api/management/admin/led-instances";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListLedInstancesUseCase listLedInstancesUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private LedInstanceListResponse sampleResponse() {
        LedInstanceListResponse.LedInstanceItem unboundItem = LedInstanceListResponse.LedInstanceItem.builder()
                .instanceId(2L)
                .modelId(101L)
                .modelName("Lumintra Matrix 64x64 Pro")
                .serialNumber("LMT-64X64-0001")
                .macAddress("AA:BB:CC:11:22:01")
                .hardwareRevision("REV_1_0")
                .deviceName(null)
                .status(LedInstanceStatus.IN_STOCK)
                .fulfillment(null)
                .owner(null)
                .boundAt(null)
                .warrantyExpiresAt(null)
                .manufacturedAt(Instant.parse("2026-10-15T06:00:00Z"))
                .qrPayload("{\"v\":1,\"sn\":\"LMT-64X64-0001\",\"mac\":\"AA:BB:CC:11:22:01\",\"sig\":\"abc123\"}")
                .createdAt(Instant.parse("2026-11-14T06:00:00Z"))
                .updatedAt(Instant.parse("2026-11-14T06:00:00Z"))
                .build();

        LedInstanceListResponse.LedInstanceItem boundItem = LedInstanceListResponse.LedInstanceItem.builder()
                .instanceId(1L)
                .modelId(101L)
                .modelName("Lumintra Matrix 64x64 Pro")
                .serialNumber("LMT-64X64-0002")
                .macAddress("AA:BB:CC:11:22:02")
                .hardwareRevision("REV_1_0")
                .deviceName("Windshield Pro")
                .status(LedInstanceStatus.BOUND)
                .fulfillment(new LedInstanceListResponse.FulfillmentInfo(55L, 102L))
                .owner(new LedInstanceListResponse.OwnerInfo(42L, "Nguyen Van A", "0901234567", "customer@example.com"))
                .boundAt(Instant.parse("2026-11-09T06:00:00Z"))
                .warrantyExpiresAt(Instant.parse("2027-05-08T06:00:00Z"))
                .manufacturedAt(Instant.parse("2026-10-15T06:00:00Z"))
                .qrPayload(null)
                .createdAt(Instant.parse("2026-11-01T06:00:00Z"))
                .updatedAt(Instant.parse("2026-11-09T06:00:00Z"))
                .build();

        PaginationMeta meta = PaginationMeta.builder()
                .page(0)
                .size(20)
                .totalElements(2)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        return new LedInstanceListResponse(List.of(unboundItem, boundItem), meta);
    }

    @Test
    @DisplayName("DEV-173 API-01 [N]: ADMIN and MANAGER receive 200 OK with LED instances, fulfillment, owner, warranty, and QR payload")
    void listLedInstances_adminAndManager_returns200WithEnvelope() throws Exception {
        when(listLedInstancesUseCase.execute(any(LedInstanceFilterRequest.class))).thenReturn(sampleResponse());

        for (String role : new String[]{"ADMIN", "MANAGER"}) {
            mockMvc.perform(get(PATH)
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].instanceId").value(2))
                    .andExpect(jsonPath("$.data[0].modelId").value(101))
                    .andExpect(jsonPath("$.data[0].modelName").value("Lumintra Matrix 64x64 Pro"))
                    .andExpect(jsonPath("$.data[0].serialNumber").value("LMT-64X64-0001"))
                    .andExpect(jsonPath("$.data[0].macAddress").value("AA:BB:CC:11:22:01"))
                    .andExpect(jsonPath("$.data[0].status").value("IN_STOCK"))
                    .andExpect(jsonPath("$.data[0].qrPayload").isString())
                    .andExpect(jsonPath("$.data[1].instanceId").value(1))
                    .andExpect(jsonPath("$.data[1].status").value("BOUND"))
                    .andExpect(jsonPath("$.data[1].fulfillment.orderId").value(55))
                    .andExpect(jsonPath("$.data[1].fulfillment.orderItemId").value(102))
                    .andExpect(jsonPath("$.data[1].owner.customerId").value(42))
                    .andExpect(jsonPath("$.data[1].owner.fullName").value("Nguyen Van A"))
                    .andExpect(jsonPath("$.data[1].boundAt").value("2026-11-09T06:00:00Z"))
                    .andExpect(jsonPath("$.data[1].warrantyExpiresAt").value("2027-05-08T06:00:00Z"))
                    .andExpect(jsonPath("$.data[1].qrPayload").value(nullValue()))
                    .andExpect(jsonPath("$.meta.page").value(0))
                    .andExpect(jsonPath("$.meta.size").value(20))
                    .andExpect(jsonPath("$.meta.totalElements").value(2));
        }

        verify(listLedInstancesUseCase, times(2))
                .execute(new LedInstanceFilterRequest(null, null, null, null, null));
    }

    @Test
    @DisplayName("DEV-173 API-02 [N/B]: Query filters and boundary values bind and normalize cleanly")
    void listLedInstances_withFiltersAndBoundaries_bindsCleanly() throws Exception {
        when(listLedInstancesUseCase.execute(any(LedInstanceFilterRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("MANAGER"))
                        .param("page", "1")
                        .param("size", "50")
                        .param("modelId", "101")
                        .param("status", "IN_TRANSIT")
                        .param("search", "  aa:bb:cc  "))
                .andExpect(status().isOk());

        verify(listLedInstancesUseCase).execute(new LedInstanceFilterRequest(
                1, 50, 101L, LedInstanceStatus.IN_TRANSIT, "aa:bb:cc"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("MANAGER"))
                        .param("page", "0")
                        .param("size", "100")
                        .param("search", "   "))
                .andExpect(status().isOk());

        verify(listLedInstancesUseCase).execute(new LedInstanceFilterRequest(
                0, 100, null, null, null));
    }

    @Test
    @DisplayName("DEV-173 API-03 [A]: Invalid pagination, modelId, or status enum return 400 VALIDATION_FAILED")
    void listLedInstances_invalidParams_returns400ValidationFailed() throws Exception {
        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("MANAGER"))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("MANAGER"))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("MANAGER"))
                        .param("modelId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("MANAGER"))
                        .param("status", "UNKNOWN_STATE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listLedInstancesUseCase);
    }

    @Test
    @DisplayName("DEV-173 API-04 [A]: Unauthenticated and invalid bearer requests return 401 UNAUTHORIZED")
    void listLedInstances_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get(PATH).header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(listLedInstancesUseCase);
    }

    @Test
    @DisplayName("DEV-173 API-05 [A]: STAFF, DESIGNER, and CUSTOMER roles return 403 FORBIDDEN")
    void listLedInstances_nonManagerRoles_returns403() throws Exception {
        for (String role : new String[]{"STAFF", "DESIGNER", "CUSTOMER"}) {
            mockMvc.perform(get(PATH)
                            .with(SecurityMockMvcRequestPostProcessors.user("15").roles(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        verifyNoInteractions(listLedInstancesUseCase);
    }

    @Test
    @DisplayName("DEV-173 API-06 [A]: Unexpected runtime exception returns 500 INTERNAL_SERVER_ERROR")
    void listLedInstances_unexpectedFailure_returns500() throws Exception {
        when(listLedInstancesUseCase.execute(any()))
                .thenThrow(new IllegalStateException("Unexpected persistence error"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("MANAGER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }
}
