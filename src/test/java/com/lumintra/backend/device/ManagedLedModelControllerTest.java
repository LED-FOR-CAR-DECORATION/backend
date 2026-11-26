package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.ManagedLedModelController;
import com.lumintra.backend.device.api.request.LedModelFilterRequest;
import com.lumintra.backend.device.api.request.UpdateLedModelRequest;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.application.CreateLedModelUseCase;
import com.lumintra.backend.device.application.DeprecateLedModelUseCase;
import com.lumintra.backend.device.application.ListLedModelsUseCase;
import com.lumintra.backend.device.application.UpdateLedModelUseCase;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.device.DuplicateLedModelNameException;
import com.lumintra.backend.shared.exception.device.LedModelDimensionsLockedException;
import com.lumintra.backend.shared.exception.device.LedModelNotFoundException;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ManagedLedModelController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ManagedLedModelControllerTest {

    private static final String PATH = "/api/management/admin/led-models";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListLedModelsUseCase listLedModelsUseCase;

    @MockitoBean
    private CreateLedModelUseCase createLedModelUseCase;

    @MockitoBean
    private UpdateLedModelUseCase updateLedModelUseCase;

    @MockitoBean
    private DeprecateLedModelUseCase deprecateLedModelUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private LedModelListResponse sampleResponse() {
        LedModelListResponse.LedModelItem linkedItem = LedModelListResponse.LedModelItem.builder()
                .modelId(10L)
                .productId(101L)
                .modelName("Lumintra Matrix 64x64 Pro")
                .stockQuantity(50L)
                .isLowStock(false)
                .status(LedModelStatus.ACTIVE)
                .spec(LedModelListResponse.LedSpec.builder()
                        .widthPx(64)
                        .heightPx(64)
                        .attributes(List.of(
                                new LedModelListResponse.SpecAttribute("Pixel Pitch", "P3"),
                                new LedModelListResponse.SpecAttribute("Voltage", "5V USB-C")
                        ))
                        .build())
                .activeFirmware(LedModelListResponse.ActiveFirmwareSummary.builder()
                        .firmwareId(12L)
                        .version("1.2.0")
                        .uploadedAt(java.time.Instant.parse("2026-11-20T10:00:00Z"))
                        .installedDeviceCount(5)
                        .build())
                .build();

        LedModelListResponse.LedModelItem unlinkedLowStockItem = LedModelListResponse.LedModelItem.builder()
                .modelId(9L)
                .productId(null)
                .modelName("Lumintra Matrix 32x64 Draft")
                .stockQuantity(3L)
                .isLowStock(true)
                .status(LedModelStatus.ACTIVE)
                .spec(LedModelListResponse.LedSpec.builder()
                        .widthPx(32)
                        .heightPx(64)
                        .attributes(List.of())
                        .build())
                .build();

        PaginationMeta meta = PaginationMeta.builder()
                .page(0)
                .size(20)
                .totalElements(2)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        return new LedModelListResponse(List.of(linkedItem, unlinkedLowStockItem), meta);
    }

    @Test
    @DisplayName("DEV-01 UTCID01 [N]: ADMIN, MANAGER, and STAFF receive 200 OK with LED model catalog fields and pagination meta")
    void listLedModels_allowedRoles_returns200WithEnvelope() throws Exception {
        when(listLedModelsUseCase.execute(any(LedModelFilterRequest.class))).thenReturn(sampleResponse());

        for (String role : new String[]{"ADMIN", "MANAGER", "STAFF"}) {
            mockMvc.perform(get(PATH)
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].modelId").value(10))
                    .andExpect(jsonPath("$.data[0].productId").value(101))
                    .andExpect(jsonPath("$.data[0].modelName").value("Lumintra Matrix 64x64 Pro"))
                    .andExpect(jsonPath("$.data[0].stockQuantity").value(50))
                    .andExpect(jsonPath("$.data[0].isLowStock").value(false))
                    .andExpect(jsonPath("$.data[0].lowStock").doesNotExist())
                    .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data[0].spec.widthPx").value(64))
                    .andExpect(jsonPath("$.data[0].spec.heightPx").value(64))
                    .andExpect(jsonPath("$.data[0].spec.attributes[0].label").value("Pixel Pitch"))
                    .andExpect(jsonPath("$.data[0].spec.attributes[0].value").value("P3"))
                    .andExpect(jsonPath("$.data[0].spec.attributes[1].label").value("Voltage"))
                    .andExpect(jsonPath("$.data[1].modelId").value(9))
                    .andExpect(jsonPath("$.data[1].productId").value(nullValue()))
                    .andExpect(jsonPath("$.data[1].isLowStock").value(true))
                    .andExpect(jsonPath("$.data[0].activeFirmware.firmwareId").value(12))
                    .andExpect(jsonPath("$.data[0].activeFirmware.version").value("1.2.0"))
                    .andExpect(jsonPath("$.data[0].activeFirmware.uploadedAt").value("2026-11-20T10:00:00Z"))
                    .andExpect(jsonPath("$.data[0].activeFirmware.installedDeviceCount").value(5))
                    .andExpect(jsonPath("$.data[1].activeFirmware").value(nullValue()))
                    .andExpect(jsonPath("$.meta.page").value(0))
                    .andExpect(jsonPath("$.meta.size").value(20))
                    .andExpect(jsonPath("$.meta.totalElements").value(2));
        }

        verify(listLedModelsUseCase, times(3))
                .execute(new LedModelFilterRequest(null, null, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("DEV-01 UTCID02 [N]: All query filter parameters bind and normalize cleanly")
    void listLedModels_withAllFilters_bindsNormalizedFilterRequest() throws Exception {
        when(listLedModelsUseCase.execute(any(LedModelFilterRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("MANAGER"))
                        .param("page", "1")
                        .param("size", "25")
                        .param("status", "OUT_OF_STOCK")
                        .param("widthPx", "64")
                        .param("heightPx", "32")
                        .param("unlinkedToProductOnly", "true")
                        .param("lowStockOnly", "true")
                        .param("keyword", "  Matrix 64  "))
                .andExpect(status().isOk());

        verify(listLedModelsUseCase).execute(new LedModelFilterRequest(
                1,
                25,
                LedModelStatus.OUT_OF_STOCK,
                64,
                32,
                true,
                true,
                "Matrix 64"
        ));
    }

    @Test
    @DisplayName("DEV-01 UTCID03 [B]: Boundary pagination and dimension values bind successfully")
    void listLedModels_boundaryValues_returns200() throws Exception {
        when(listLedModelsUseCase.execute(any(LedModelFilterRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .param("page", "0")
                        .param("size", "100")
                        .param("widthPx", "4096")
                        .param("heightPx", "1")
                        .param("keyword", "   "))
                .andExpect(status().isOk());

        verify(listLedModelsUseCase).execute(new LedModelFilterRequest(
                0,
                100,
                null,
                4096,
                1,
                null,
                null,
                null
        ));
    }

    @Test
    @DisplayName("DEV-01 UTCID04 [A]: Invalid filter params or status enum return 400 VALIDATION_FAILED")
    void listLedModels_invalidParams_returns400ValidationFailed() throws Exception {
        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN"))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN"))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN"))
                        .param("widthPx", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN"))
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listLedModelsUseCase);
    }

    @Test
    @DisplayName("DEV-01 UTCID05 [A]: Unauthenticated and invalid bearer requests return 401 UNAUTHORIZED")
    void listLedModels_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get(PATH).header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(listLedModelsUseCase);
    }

    @Test
    @DisplayName("DEV-01 UTCID06 [A]: CUSTOMER and DESIGNER roles return 403 FORBIDDEN")
    void listLedModels_nonInternalRoles_returns403() throws Exception {
        for (String role : new String[]{"CUSTOMER", "DESIGNER"}) {
            mockMvc.perform(get(PATH)
                            .with(SecurityMockMvcRequestPostProcessors.user("15").roles(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        verifyNoInteractions(listLedModelsUseCase);
    }

    @Test
    @DisplayName("DEV-01 UTCID07 [A]: Unexpected runtime exception returns 500 INTERNAL_SERVER_ERROR")
    void listLedModels_unexpectedFailure_returns500() throws Exception {
        when(listLedModelsUseCase.execute(any()))
                .thenThrow(new IllegalStateException("Unexpected database failure"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    @DisplayName("DEV-02 UTCID01 [N]: ADMIN role creates LED model successfully (201 Created)")
    void createLedModel_asAdmin_returns201() throws Exception {
        LedModelListResponse.LedModelItem createdItem = LedModelListResponse.LedModelItem.builder()
                .modelId(101L)
                .productId(null)
                .modelName("Lumintra Matrix 64x64 Pro V2")
                .stockQuantity(25L)
                .isLowStock(false)
                .status(LedModelStatus.ACTIVE)
                .spec(LedModelListResponse.LedSpec.builder()
                        .widthPx(64)
                        .heightPx(64)
                        .attributes(List.of(
                                new LedModelListResponse.SpecAttribute("Điện áp", "5V USB-C"),
                                new LedModelListResponse.SpecAttribute("Khoảng cách điểm ảnh", "P4 (4mm)")
                        ))
                        .build())
                .build();

        when(createLedModelUseCase.execute(any())).thenReturn(createdItem);

        String requestBody = """
                {
                  "modelName": "Lumintra Matrix 64x64 Pro V2",
                  "stockQuantity": 25,
                  "spec": {
                    "widthPx": 64,
                    "heightPx": 64,
                    "attributes": [
                      {
                        "label": "Điện áp",
                        "value": "5V USB-C"
                      },
                      {
                        "label": "Khoảng cách điểm ảnh",
                        "value": "P4 (4mm)"
                      }
                    ]
                  }
                }
                """;

        mockMvc.perform(post(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.modelId").value(101))
                .andExpect(jsonPath("$.data.productId").value(nullValue()))
                .andExpect(jsonPath("$.data.modelName").value("Lumintra Matrix 64x64 Pro V2"))
                .andExpect(jsonPath("$.data.stockQuantity").value(25))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.isLowStock").value(false))
                .andExpect(jsonPath("$.data.spec.widthPx").value(64))
                .andExpect(jsonPath("$.data.spec.heightPx").value(64))
                .andExpect(jsonPath("$.data.spec.attributes[0].label").value("Điện áp"))
                .andExpect(jsonPath("$.data.spec.attributes[0].value").value("5V USB-C"));

        verify(createLedModelUseCase, times(1)).execute(any());
    }

    @Test
    @DisplayName("DEV-02 UTCID02 [N]: MANAGER role creates LED model successfully (201 Created)")
    void createLedModel_asManager_returns201() throws Exception {
        LedModelListResponse.LedModelItem createdItem = LedModelListResponse.LedModelItem.builder()
                .modelId(102L)
                .productId(null)
                .modelName("Lumintra Matrix 32x32 Mini")
                .stockQuantity(0L)
                .isLowStock(true)
                .status(LedModelStatus.OUT_OF_STOCK)
                .spec(LedModelListResponse.LedSpec.builder()
                        .widthPx(32)
                        .heightPx(32)
                        .attributes(List.of())
                        .build())
                .build();

        when(createLedModelUseCase.execute(any())).thenReturn(createdItem);

        String requestBody = """
                {
                  "modelName": "Lumintra Matrix 32x32 Mini",
                  "stockQuantity": 0,
                  "spec": {
                    "widthPx": 32,
                    "heightPx": 32,
                    "attributes": []
                  }
                }
                """;

        mockMvc.perform(post(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("8").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.modelId").value(102))
                .andExpect(jsonPath("$.data.status").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.data.stockQuantity").value(0))
                .andExpect(jsonPath("$.data.isLowStock").value(true));

        verify(createLedModelUseCase, times(1)).execute(any());
    }

    @Test
    @DisplayName("DEV-02 UTCID03 [A]: Unauthenticated request to create LED model returns 401 UNAUTHORIZED")
    void createLedModel_unauthenticated_returns401() throws Exception {
        String requestBody = """
                {
                  "modelName": "Lumintra Matrix 64x64",
                  "stockQuantity": 10,
                  "spec": { "widthPx": 64, "heightPx": 64 }
                }
                """;

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(createLedModelUseCase);
    }

    @Test
    @DisplayName("DEV-02 UTCID04 [A]: STAFF, CUSTOMER, and DESIGNER roles return 403 FORBIDDEN")
    void createLedModel_forbiddenRoles_returns403() throws Exception {
        String requestBody = """
                {
                  "modelName": "Lumintra Matrix 64x64",
                  "stockQuantity": 10,
                  "spec": { "widthPx": 64, "heightPx": 64 }
                }
                """;

        for (String role : new String[]{"STAFF", "CUSTOMER", "DESIGNER"}) {
            mockMvc.perform(post(PATH)
                            .with(SecurityMockMvcRequestPostProcessors.user("12").roles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        verifyNoInteractions(createLedModelUseCase);
    }

    @Test
    @DisplayName("DEV-02 UTCID05 [B]: Blank modelName returns 400 VALIDATION_FAILED")
    void createLedModel_blankModelName_returns400() throws Exception {
        String requestBody = """
                {
                  "modelName": "   ",
                  "stockQuantity": 10,
                  "spec": { "widthPx": 64, "heightPx": 64 }
                }
                """;

        mockMvc.perform(post(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createLedModelUseCase);
    }

    @Test
    @DisplayName("DEV-02 UTCID06 [B]: Negative stockQuantity returns 400 VALIDATION_FAILED")
    void createLedModel_negativeStock_returns400() throws Exception {
        String requestBody = """
                {
                  "modelName": "Lumintra Matrix 64x64",
                  "stockQuantity": -5,
                  "spec": { "widthPx": 64, "heightPx": 64 }
                }
                """;

        mockMvc.perform(post(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createLedModelUseCase);
    }

    @Test
    @DisplayName("DEV-02 UTCID07 [B]: Non-positive widthPx or heightPx returns 400 VALIDATION_FAILED")
    void createLedModel_invalidDimensions_returns400() throws Exception {
        String invalidWidth = """
                {
                  "modelName": "Lumintra Matrix 64x64",
                  "stockQuantity": 10,
                  "spec": { "widthPx": 0, "heightPx": 64 }
                }
                """;

        mockMvc.perform(post(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidWidth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String invalidHeight = """
                {
                  "modelName": "Lumintra Matrix 64x64",
                  "stockQuantity": 10,
                  "spec": { "widthPx": 64, "heightPx": -10 }
                }
                """;

        mockMvc.perform(post(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidHeight))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createLedModelUseCase);
    }

    @Test
    @DisplayName("DEV-02 UTCID08 [A]: Duplicate modelName returns 409 CONFLICT with DUPLICATE_LED_MODEL_NAME")
    void createLedModel_duplicateName_returns409() throws Exception {
        when(createLedModelUseCase.execute(any()))
                .thenThrow(new DuplicateLedModelNameException("Lumintra Matrix 64x64 Pro"));

        String requestBody = """
                {
                  "modelName": "Lumintra Matrix 64x64 Pro",
                  "stockQuantity": 10,
                  "spec": { "widthPx": 64, "heightPx": 64 }
                }
                """;

        mockMvc.perform(post(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LED_MODEL_NAME"))
                .andExpect(jsonPath("$.message").value("LED model with name 'Lumintra Matrix 64x64 Pro' already exists."));

        verify(createLedModelUseCase, times(1)).execute(any());
    }

    // =========================================================================
    // DEV-03: PUT /api/management/admin/led-models/{id} Tests
    // =========================================================================

    @Test
    @DisplayName("DEV-03 UTCID01 [N]: ADMIN role updates LED model successfully returns 200 OK")
    void updateLedModel_asAdmin_returns200() throws Exception {
        LedModelListResponse.LedModelItem updatedItem = LedModelListResponse.LedModelItem.builder()
                .modelId(10L)
                .productId(101L)
                .modelName("Lumintra Matrix 64x64 Pro v2")
                .stockQuantity(25L)
                .isLowStock(false)
                .status(LedModelStatus.ACTIVE)
                .spec(LedModelListResponse.LedSpec.builder()
                        .widthPx(64)
                        .heightPx(64)
                        .attributes(List.of(
                                new LedModelListResponse.SpecAttribute("Pitch", "P2.5"),
                                new LedModelListResponse.SpecAttribute("Power", "USB-C 10W")
                        ))
                        .build())
                .build();

        when(updateLedModelUseCase.execute(eq(10L), any(UpdateLedModelRequest.class)))
                .thenReturn(updatedItem);

        String requestBody = """
                {
                  "modelName": "Lumintra Matrix 64x64 Pro v2",
                  "stockQuantity": 25,
                  "spec": {
                    "widthPx": 64,
                    "heightPx": 64,
                    "attributes": [
                      { "label": "Pitch", "value": "P2.5" },
                      { "label": "Power", "value": "USB-C 10W" }
                    ]
                  }
                }
                """;

        mockMvc.perform(put(PATH + "/{id}", 10L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelId").value(10L))
                .andExpect(jsonPath("$.data.productId").value(101L))
                .andExpect(jsonPath("$.data.modelName").value("Lumintra Matrix 64x64 Pro v2"))
                .andExpect(jsonPath("$.data.stockQuantity").value(25))
                .andExpect(jsonPath("$.data.isLowStock").value(false))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.spec.widthPx").value(64))
                .andExpect(jsonPath("$.data.spec.heightPx").value(64))
                .andExpect(jsonPath("$.data.spec.attributes[0].label").value("Pitch"))
                .andExpect(jsonPath("$.data.spec.attributes[0].value").value("P2.5"))
                .andExpect(jsonPath("$.data.spec.attributes[1].label").value("Power"))
                .andExpect(jsonPath("$.data.spec.attributes[1].value").value("USB-C 10W"));

        verify(updateLedModelUseCase, times(1)).execute(eq(10L), any(UpdateLedModelRequest.class));
    }

    @Test
    @DisplayName("DEV-03 UTCID02 [N]: MANAGER role updates LED model successfully returns 200 OK")
    void updateLedModel_asManager_returns200() throws Exception {
        LedModelListResponse.LedModelItem updatedItem = LedModelListResponse.LedModelItem.builder()
                .modelId(5L)
                .productId(null)
                .modelName("Lumintra Matrix 32x32 Eco")
                .stockQuantity(0L)
                .isLowStock(true)
                .status(LedModelStatus.OUT_OF_STOCK)
                .spec(LedModelListResponse.LedSpec.builder()
                        .widthPx(32)
                        .heightPx(32)
                        .attributes(List.of())
                        .build())
                .build();

        when(updateLedModelUseCase.execute(eq(5L), any(UpdateLedModelRequest.class)))
                .thenReturn(updatedItem);

        String requestBody = """
                {
                  "modelName": "Lumintra Matrix 32x32 Eco",
                  "stockQuantity": 0,
                  "spec": {
                    "widthPx": 32,
                    "heightPx": 32,
                    "attributes": []
                  }
                }
                """;

        mockMvc.perform(put(PATH + "/{id}", 5L)
                        .with(SecurityMockMvcRequestPostProcessors.user("2").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelId").value(5L))
                .andExpect(jsonPath("$.data.stockQuantity").value(0))
                .andExpect(jsonPath("$.data.status").value("OUT_OF_STOCK"));

        verify(updateLedModelUseCase, times(1)).execute(eq(5L), any(UpdateLedModelRequest.class));
    }

    @Test
    @DisplayName("DEV-03 UTCID03 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void updateLedModel_unauthenticated_returns401() throws Exception {
        String requestBody = """
                {
                  "modelName": "Lumintra Matrix 64x64",
                  "stockQuantity": 10,
                  "spec": { "widthPx": 64, "heightPx": 64 }
                }
                """;

        mockMvc.perform(put(PATH + "/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(updateLedModelUseCase);
    }

    @Test
    @DisplayName("DEV-03 UTCID04 [A]: Non-management roles (STAFF, CUSTOMER, DESIGNER) return 403 FORBIDDEN")
    void updateLedModel_unauthorizedRoles_returns403() throws Exception {
        String requestBody = """
                {
                  "modelName": "Lumintra Matrix 64x64",
                  "stockQuantity": 10,
                  "spec": { "widthPx": 64, "heightPx": 64 }
                }
                """;

        mockMvc.perform(put(PATH + "/{id}", 1L)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(put(PATH + "/{id}", 1L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(put(PATH + "/{id}", 1L)
                        .with(SecurityMockMvcRequestPostProcessors.user("5").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(updateLedModelUseCase);
    }

    @Test
    @DisplayName("DEV-03 UTCID05 [A]: Non-existent modelId returns 404 NOT_FOUND with LED_MODEL_NOT_FOUND")
    void updateLedModel_notFound_returns404() throws Exception {
        when(updateLedModelUseCase.execute(eq(999L), any(UpdateLedModelRequest.class)))
                .thenThrow(new LedModelNotFoundException(999L));

        String requestBody = """
                {
                  "modelName": "Lumintra Matrix NonExistent",
                  "stockQuantity": 10,
                  "spec": { "widthPx": 64, "heightPx": 64 }
                }
                """;

        mockMvc.perform(put(PATH + "/{id}", 999L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LED_MODEL_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("LED model with id 999 not found."));

        verify(updateLedModelUseCase, times(1)).execute(eq(999L), any(UpdateLedModelRequest.class));
    }

    @Test
    @DisplayName("DEV-03 UTCID06 [A]: Dimension change when instances exist returns 409 CONFLICT with LED_MODEL_DIMENSIONS_LOCKED")
    void updateLedModel_dimensionsLocked_returns409() throws Exception {
        when(updateLedModelUseCase.execute(eq(10L), any(UpdateLedModelRequest.class)))
                .thenThrow(new LedModelDimensionsLockedException(10L));

        String requestBody = """
                {
                  "modelName": "Lumintra Matrix 32x64",
                  "stockQuantity": 10,
                  "spec": { "widthPx": 32, "heightPx": 64 }
                }
                """;

        mockMvc.perform(put(PATH + "/{id}", 10L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LED_MODEL_DIMENSIONS_LOCKED"))
                .andExpect(jsonPath("$.message").value("Cannot modify canvas dimensions for LED model with id 10: physical LED instances have already been provisioned."));

        verify(updateLedModelUseCase, times(1)).execute(eq(10L), any(UpdateLedModelRequest.class));
    }

    @Test
    @DisplayName("DEV-03 UTCID07 [A]: Duplicate modelName returns 409 CONFLICT with DUPLICATE_LED_MODEL_NAME")
    void updateLedModel_duplicateName_returns409() throws Exception {
        when(updateLedModelUseCase.execute(eq(10L), any(UpdateLedModelRequest.class)))
                .thenThrow(new DuplicateLedModelNameException("Existing LED Matrix"));

        String requestBody = """
                {
                  "modelName": "Existing LED Matrix",
                  "stockQuantity": 10,
                  "spec": { "widthPx": 64, "heightPx": 64 }
                }
                """;

        mockMvc.perform(put(PATH + "/{id}", 10L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LED_MODEL_NAME"))
                .andExpect(jsonPath("$.message").value("LED model with name 'Existing LED Matrix' already exists."));

        verify(updateLedModelUseCase, times(1)).execute(eq(10L), any(UpdateLedModelRequest.class));
    }

    @Test
    @DisplayName("DEV-03 UTCID08 [B]: Invalid request parameters return 400 VALIDATION_FAILED")
    void updateLedModel_validationFailure_returns400() throws Exception {
        String blankName = """
                {
                  "modelName": "",
                  "stockQuantity": 10,
                  "spec": { "widthPx": 64, "heightPx": 64 }
                }
                """;

        mockMvc.perform(put(PATH + "/{id}", 10L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankName))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String negativeStock = """
                {
                  "modelName": "Lumintra Matrix 64x64",
                  "stockQuantity": -1,
                  "spec": { "widthPx": 64, "heightPx": 64 }
                }
                """;

        mockMvc.perform(put(PATH + "/{id}", 10L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(negativeStock))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String invalidDimensions = """
                {
                  "modelName": "Lumintra Matrix 64x64",
                  "stockQuantity": 10,
                  "spec": { "widthPx": -5, "heightPx": 64 }
                }
                """;

        mockMvc.perform(put(PATH + "/{id}", 10L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidDimensions))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(updateLedModelUseCase);
    }

    @Test
    @DisplayName("DEV-03 UTCID09 [B]: Path variable id <= 0 returns 400 VALIDATION_FAILED")
    void updateLedModel_invalidPathId_returns400() throws Exception {
        String requestBody = """
                {
                  "modelName": "Lumintra Matrix 64x64",
                  "stockQuantity": 10,
                  "spec": { "widthPx": 64, "heightPx": 64 }
                }
                """;

        mockMvc.perform(put(PATH + "/{id}", 0L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(updateLedModelUseCase);
    }

    // =========================================================================
    // DEV-04: DELETE /api/management/admin/led-models/{id} Tests
    // =========================================================================

    @Test
    @DisplayName("DEV-04 UTCID01 [N]: ADMIN role deprecates LED model returns 204 NO_CONTENT")
    void deprecateLedModel_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete(PATH + "/{id}", 10L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN")))
                .andExpect(status().isNoContent());

        verify(deprecateLedModelUseCase, times(1)).execute(10L);
    }

    @Test
    @DisplayName("DEV-04 UTCID02 [N]: MANAGER role deprecates LED model returns 204 NO_CONTENT")
    void deprecateLedModel_asManager_returns204() throws Exception {
        mockMvc.perform(delete(PATH + "/{id}", 5L)
                        .with(SecurityMockMvcRequestPostProcessors.user("2").roles("MANAGER")))
                .andExpect(status().isNoContent());

        verify(deprecateLedModelUseCase, times(1)).execute(5L);
    }

    @Test
    @DisplayName("DEV-04 UTCID03 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void deprecateLedModel_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete(PATH + "/{id}", 10L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(deprecateLedModelUseCase);
    }

    @Test
    @DisplayName("DEV-04 UTCID04 [A]: Non-management roles (STAFF, CUSTOMER, DESIGNER) return 403 FORBIDDEN")
    void deprecateLedModel_unauthorizedRoles_returns403() throws Exception {
        for (String role : new String[]{"STAFF", "CUSTOMER", "DESIGNER"}) {
            mockMvc.perform(delete(PATH + "/{id}", 10L)
                            .with(SecurityMockMvcRequestPostProcessors.user("12").roles(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        verifyNoInteractions(deprecateLedModelUseCase);
    }

    @Test
    @DisplayName("DEV-04 UTCID05 [A]: Non-existent modelId returns 404 NOT_FOUND with LED_MODEL_NOT_FOUND")
    void deprecateLedModel_notFound_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new LedModelNotFoundException(999L))
                .when(deprecateLedModelUseCase).execute(999L);

        mockMvc.perform(delete(PATH + "/{id}", 999L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LED_MODEL_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("LED model with id 999 not found."));

        verify(deprecateLedModelUseCase, times(1)).execute(999L);
    }

    @Test
    @DisplayName("DEV-04 UTCID06 [B]: Path variable id <= 0 returns 400 VALIDATION_FAILED")
    void deprecateLedModel_invalidPathId_returns400() throws Exception {
        mockMvc.perform(delete(PATH + "/{id}", 0L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(deprecateLedModelUseCase);
    }
}
