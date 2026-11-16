package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.DeviceWarrantyController;
import com.lumintra.backend.device.api.request.SubmitWarrantyClaimRequest;
import com.lumintra.backend.device.api.response.CustomerDeviceWarrantyResponse;
import com.lumintra.backend.device.api.response.WarrantyClaimResponse;
import com.lumintra.backend.device.application.GetCustomerDeviceWarrantyUseCase;
import com.lumintra.backend.device.application.SubmitWarrantyClaimUseCase;
import com.lumintra.backend.device.domain.model.WarrantyStatus;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import com.lumintra.backend.shared.exception.device.WarrantyClaimConflictException;
import com.lumintra.backend.shared.exception.device.WarrantyExpiredException;
import com.lumintra.backend.shared.exception.device.WarrantyNotActiveException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeviceWarrantyController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        GlobalExceptionHandler.class
})
@ActiveProfiles("test")
class DeviceWarrantyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitWarrantyClaimUseCase submitWarrantyClaimUseCase;

    @MockitoBean
    private GetCustomerDeviceWarrantyUseCase getCustomerDeviceWarrantyUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private static final String VALID_PAYLOAD_JSON = """
            {
              "issueDescription": "Màn hình LED bị chớp nháy sau 15 phút sử dụng.",
              "evidenceMediaUrls": [
                "https://cdn.lumintra.shop/warranty/10/evidence-01.jpg",
                "https://cdn.lumintra.shop/warranty/10/evidence-02.jpg"
              ]
            }
            """;

    // ==========================================
    // DEV-14: POST /api/devices/{id}/warranty
    // ==========================================

    @Test
    @WithMockUser(username = "5", roles = {"CUSTOMER"})
    @DisplayName("DEV-14 CTRL-01 [N]: CUSTOMER submits valid warranty claim and receives 201 Created")
    void submitWarrantyClaim_whenAuthenticatedCustomer_returns201Created() throws Exception {
        WarrantyClaimResponse response = new WarrantyClaimResponse(
                1001L,
                10L,
                "LUM-6432-RGB-2026-0001",
                "Xe Wave Alpha",
                WarrantyTicketStatus.PENDING,
                "Màn hình LED bị chớp nháy sau 15 phút sử dụng.",
                List.of(
                        "https://cdn.lumintra.shop/warranty/10/evidence-01.jpg",
                        "https://cdn.lumintra.shop/warranty/10/evidence-02.jpg"
                ),
                null,
                Instant.parse("2026-11-16T15:40:00Z")
        );

        when(submitWarrantyClaimUseCase.execute(eq(5L), eq(10L), any(SubmitWarrantyClaimRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/devices/10/warranty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ticketId").value(1001))
                .andExpect(jsonPath("$.data.deviceId").value(10))
                .andExpect(jsonPath("$.data.deviceSerialNumber").value("LUM-6432-RGB-2026-0001"))
                .andExpect(jsonPath("$.data.deviceName").value("Xe Wave Alpha"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.issueDescription").value("Màn hình LED bị chớp nháy sau 15 phút sử dụng."))
                .andExpect(jsonPath("$.data.evidenceMediaUrls[0]").value("https://cdn.lumintra.shop/warranty/10/evidence-01.jpg"))
                .andExpect(jsonPath("$.data.evidenceMediaUrls[1]").value("https://cdn.lumintra.shop/warranty/10/evidence-02.jpg"))
                .andExpect(jsonPath("$.data.resolutionNotes").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-16T15:40:00Z"));
    }

    @Test
    @DisplayName("DEV-14 CTRL-02 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void submitWarrantyClaim_whenUnauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/devices/10/warranty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = "8", roles = {"DESIGNER"})
    @DisplayName("DEV-14 CTRL-03 [A]: Non-customer role returns 403 FORBIDDEN")
    void submitWarrantyClaim_whenNonCustomerRole_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/devices/10/warranty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "invalid-user-id", roles = {"CUSTOMER"})
    @DisplayName("DEV-14 CTRL-04 [A]: Non-numeric customer ID in token returns 401 INVALID_CREDENTIALS")
    void submitWarrantyClaim_whenInvalidSubject_returns401() throws Exception {
        mockMvc.perform(post("/api/devices/10/warranty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @WithMockUser(username = "5", roles = {"CUSTOMER"})
    @DisplayName("DEV-14 CTRL-05 [A]: Blank issue description returns 400 VALIDATION_FAILED")
    void submitWarrantyClaim_whenBlankDescription_returns400ValidationFailed() throws Exception {
        String invalidJson = """
                {
                  "issueDescription": "   ",
                  "evidenceMediaUrls": ["https://cdn.lumintra.shop/warranty/10/evidence.jpg"]
                }
                """;

        mockMvc.perform(post("/api/devices/10/warranty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "5", roles = {"CUSTOMER"})
    @DisplayName("DEV-14 CTRL-06 [A]: Issue description too short (<10 chars) returns 400 VALIDATION_FAILED")
    void submitWarrantyClaim_whenShortDescription_returns400ValidationFailed() throws Exception {
        String invalidJson = """
                {
                  "issueDescription": "Hỏng",
                  "evidenceMediaUrls": []
                }
                """;

        mockMvc.perform(post("/api/devices/10/warranty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "5", roles = {"CUSTOMER"})
    @DisplayName("DEV-14 CTRL-07 [A]: Device not found returns 404 LED_INSTANCE_NOT_FOUND")
    void submitWarrantyClaim_whenDeviceNotFound_returns404NotFound() throws Exception {
        when(submitWarrantyClaimUseCase.execute(eq(5L), eq(999L), any(SubmitWarrantyClaimRequest.class)))
                .thenThrow(new LedInstanceNotFoundException("LED hardware device not found."));

        mockMvc.perform(post("/api/devices/999/warranty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LED_INSTANCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "5", roles = {"CUSTOMER"})
    @DisplayName("DEV-14 CTRL-08 [A]: Device not owned by customer returns 403 DEVICE_OWNERSHIP_MISMATCH")
    void submitWarrantyClaim_whenDeviceNotOwned_returns403Forbidden() throws Exception {
        when(submitWarrantyClaimUseCase.execute(eq(5L), eq(10L), any(SubmitWarrantyClaimRequest.class)))
                .thenThrow(new DeviceOwnershipMismatchException("You do not own this LED hardware device."));

        mockMvc.perform(post("/api/devices/10/warranty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEVICE_OWNERSHIP_MISMATCH"));
    }

    @Test
    @WithMockUser(username = "5", roles = {"CUSTOMER"})
    @DisplayName("DEV-14 CTRL-09 [A]: Warranty not active returns 400 WARRANTY_NOT_ACTIVE")
    void submitWarrantyClaim_whenWarrantyNotActive_returns400BadRequest() throws Exception {
        when(submitWarrantyClaimUseCase.execute(eq(5L), eq(10L), any(SubmitWarrantyClaimRequest.class)))
                .thenThrow(new WarrantyNotActiveException("Warranty for this device has not been activated."));

        mockMvc.perform(post("/api/devices/10/warranty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WARRANTY_NOT_ACTIVE"));
    }

    @Test
    @WithMockUser(username = "5", roles = {"CUSTOMER"})
    @DisplayName("DEV-14 CTRL-10 [A]: Warranty expired returns 400 WARRANTY_EXPIRED")
    void submitWarrantyClaim_whenWarrantyExpired_returns400BadRequest() throws Exception {
        when(submitWarrantyClaimUseCase.execute(eq(5L), eq(10L), any(SubmitWarrantyClaimRequest.class)))
                .thenThrow(new WarrantyExpiredException("Warranty for this device has expired."));

        mockMvc.perform(post("/api/devices/10/warranty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WARRANTY_EXPIRED"));
    }

    @Test
    @WithMockUser(username = "5", roles = {"CUSTOMER"})
    @DisplayName("DEV-14 CTRL-11 [A]: Duplicate active claim returns 409 WARRANTY_CLAIM_ALREADY_EXISTS")
    void submitWarrantyClaim_whenActiveClaimAlreadyExists_returns409Conflict() throws Exception {
        when(submitWarrantyClaimUseCase.execute(eq(5L), eq(10L), any(SubmitWarrantyClaimRequest.class)))
                .thenThrow(new WarrantyClaimConflictException("An active warranty claim already exists for this device."));

        mockMvc.perform(post("/api/devices/10/warranty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WARRANTY_CLAIM_ALREADY_EXISTS"));
    }

    @Test
    @WithMockUser(username = "5", roles = {"CUSTOMER"})
    @DisplayName("DEV-14 CTRL-12 [A]: Exceeding 5 evidence URLs returns 400 VALIDATION_FAILED")
    void submitWarrantyClaim_whenMoreThan5EvidenceUrls_returns400ValidationFailed() throws Exception {
        String invalidJson = """
                {
                  "issueDescription": "Màn hình LED bị chớp nháy sau 15 phút sử dụng.",
                  "evidenceMediaUrls": [
                    "https://cdn.lumintra.shop/warranty/1.jpg",
                    "https://cdn.lumintra.shop/warranty/2.jpg",
                    "https://cdn.lumintra.shop/warranty/3.jpg",
                    "https://cdn.lumintra.shop/warranty/4.jpg",
                    "https://cdn.lumintra.shop/warranty/5.jpg",
                    "https://cdn.lumintra.shop/warranty/6.jpg"
                  ]
                }
                """;

        mockMvc.perform(post("/api/devices/10/warranty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ==========================================
    // DEV-14B: GET /api/devices/{id}/warranty
    // ==========================================

    @Test
    @WithMockUser(username = "5", roles = {"CUSTOMER"})
    @DisplayName("DEV-14B CTRL-01 [N]: CUSTOMER views warranty and claims history, returns 200 OK")
    void getDeviceWarranty_whenAuthenticatedCustomer_returns200OkWithWarrantyDetails() throws Exception {
        CustomerDeviceWarrantyResponse response = new CustomerDeviceWarrantyResponse(
                10L,
                "LUM-6432-RGB-2026-0001",
                "Xe Wave Alpha - Đồng hồ LED",
                Instant.parse("2026-10-01T10:00:00Z"),
                Instant.parse("2027-03-30T10:00:00Z"),
                WarrantyStatus.ACTIVE,
                134L,
                new CustomerDeviceWarrantyResponse.WarrantyTicketSummaryResponse(
                        1L,
                        WarrantyTicketStatus.APPROVED,
                        "Màn hình LED bị chớp nháy.",
                        List.of("https://cdn.lumintra.shop/warranty/10/img-01.jpg"),
                        "Yêu cầu hợp lệ.",
                        Instant.parse("2026-11-16T10:00:00Z"),
                        Instant.parse("2026-11-16T14:30:00Z")
                ),
                List.of(
                        new CustomerDeviceWarrantyResponse.WarrantyTicketSummaryResponse(
                                1L,
                                WarrantyTicketStatus.APPROVED,
                                "Màn hình LED bị chớp nháy.",
                                List.of("https://cdn.lumintra.shop/warranty/10/img-01.jpg"),
                                "Yêu cầu hợp lệ.",
                                Instant.parse("2026-11-16T10:00:00Z"),
                                Instant.parse("2026-11-16T14:30:00Z")
                        )
                )
        );

        when(getCustomerDeviceWarrantyUseCase.execute(5L, 10L)).thenReturn(response);

        mockMvc.perform(get("/api/devices/10/warranty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value(10))
                .andExpect(jsonPath("$.data.serialNumber").value("LUM-6432-RGB-2026-0001"))
                .andExpect(jsonPath("$.data.deviceName").value("Xe Wave Alpha - Đồng hồ LED"))
                .andExpect(jsonPath("$.data.warrantyStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.remainingDays").value(134))
                .andExpect(jsonPath("$.data.activeTicket.ticketId").value(1))
                .andExpect(jsonPath("$.data.activeTicket.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.ticketHistory[0].ticketId").value(1));
    }

    @Test
    @DisplayName("DEV-14B CTRL-02 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void getDeviceWarranty_whenUnauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/devices/10/warranty"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = "8", roles = {"DESIGNER"})
    @DisplayName("DEV-14B CTRL-03 [A]: Non-customer role returns 403 FORBIDDEN")
    void getDeviceWarranty_whenNonCustomerRole_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/devices/10/warranty"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "invalid-user-id", roles = {"CUSTOMER"})
    @DisplayName("DEV-14B CTRL-04 [A]: Non-numeric customer ID in token returns 401 INVALID_CREDENTIALS")
    void getDeviceWarranty_whenInvalidSubject_returns401InvalidCredentials() throws Exception {
        mockMvc.perform(get("/api/devices/10/warranty"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @WithMockUser(username = "5", roles = {"CUSTOMER"})
    @DisplayName("DEV-14B CTRL-05 [A]: Device not found returns 404 LED_INSTANCE_NOT_FOUND")
    void getDeviceWarranty_whenDeviceNotFound_returns404NotFound() throws Exception {
        when(getCustomerDeviceWarrantyUseCase.execute(5L, 999L))
                .thenThrow(new LedInstanceNotFoundException("LED hardware device not found."));

        mockMvc.perform(get("/api/devices/999/warranty"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LED_INSTANCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "5", roles = {"CUSTOMER"})
    @DisplayName("DEV-14B CTRL-06 [A]: Device not owned by customer returns 403 DEVICE_OWNERSHIP_MISMATCH")
    void getDeviceWarranty_whenDeviceNotOwned_returns403Forbidden() throws Exception {
        when(getCustomerDeviceWarrantyUseCase.execute(5L, 10L))
                .thenThrow(new DeviceOwnershipMismatchException("You do not own this LED hardware device."));

        mockMvc.perform(get("/api/devices/10/warranty"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEVICE_OWNERSHIP_MISMATCH"));
    }

    @Test
    @WithMockUser(username = "5", roles = {"CUSTOMER"})
    @DisplayName("DEV-14B CTRL-07 [A]: Non-positive device ID returns 400 VALIDATION_FAILED")
    void getDeviceWarranty_whenInvalidDeviceId_returns400ValidationFailed() throws Exception {
        mockMvc.perform(get("/api/devices/0/warranty"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
