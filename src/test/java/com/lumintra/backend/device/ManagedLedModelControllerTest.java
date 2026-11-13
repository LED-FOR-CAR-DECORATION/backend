package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.ManagedLedModelController;
import com.lumintra.backend.device.api.request.LedModelFilterRequest;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.application.ListLedModelsUseCase;
import com.lumintra.backend.device.domain.model.LedModelStatus;
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

@WebMvcTest(controllers = ManagedLedModelController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ManagedLedModelControllerTest {

    private static final String PATH = "/api/management/admin/led-models";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListLedModelsUseCase listLedModelsUseCase;

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
}
