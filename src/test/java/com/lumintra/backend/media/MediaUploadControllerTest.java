package com.lumintra.backend.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.media.api.controller.MediaUploadController;
import com.lumintra.backend.media.api.request.CreateSignedUploadUrlRequest;
import com.lumintra.backend.media.api.response.SignedUploadUrlResponse;
import com.lumintra.backend.media.application.CreateSignedUploadUrlUseCase;
import com.lumintra.backend.media.domain.model.MediaPurpose;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.media.StorageServiceException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MediaUploadController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class MediaUploadControllerTest {

    private static final String BASE_PATH = "/api/media/upload-url";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CreateSignedUploadUrlUseCase createSignedUploadUrlUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("MED-01 [N]: Authenticated CUSTOMER requests upload URL -> 200 OK")
    void createSignedUploadUrl_whenValidRequest_shouldReturn200Ok() throws Exception {
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "sketch.png",
                "image/png",
                102400L,
                MediaPurpose.COMMISSION_REFERENCE,
                null
        );

        SignedUploadUrlResponse response = new SignedUploadUrlResponse(
                "https://storage.googleapis.com/lumintra-private/commissions/references/4/test.png?X-Goog-Algorithm=...",
                null,
                "commissions/references/4/test.png",
                java.util.Map.of("Content-Type", "image/png", "Cache-Control", "private, max-age=600"),
                Instant.parse("2026-11-15T23:30:00Z")
        );

        when(createSignedUploadUrlUseCase.execute(eq("4"), eq(Set.of("CUSTOMER")), any(CreateSignedUploadUrlRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signedUploadUrl").value(response.signedUploadUrl()))
                .andExpect(jsonPath("$.data.publicUrl").doesNotExist())
                .andExpect(jsonPath("$.data.uploadHeaders.Cache-Control").value("private, max-age=600"))
                .andExpect(jsonPath("$.data.objectKey").value(response.objectKey()))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-11-15T23:30:00Z"));

        verify(createSignedUploadUrlUseCase).execute(eq("4"), eq(Set.of("CUSTOMER")), any(CreateSignedUploadUrlRequest.class));
    }

    @Test
    @DisplayName("MED-01 [A]: Unauthenticated request rejected -> 401 UNAUTHORIZED")
    void createSignedUploadUrl_whenUnauthenticated_shouldReturn401() throws Exception {
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "sketch.png",
                "image/png",
                102400L,
                MediaPurpose.COMMISSION_REFERENCE,
                null
        );

        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(createSignedUploadUrlUseCase);
    }

    @Test
    @DisplayName("MED-01 [A]: Access control rejection -> 403 FORBIDDEN")
    void createSignedUploadUrl_whenAccessDenied_shouldReturn403() throws Exception {
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "draft.png",
                "image/png",
                102400L,
                MediaPurpose.COMMISSION_DRAFT,
                101L
        );

        when(createSignedUploadUrlUseCase.execute(any(), any(), any()))
                .thenThrow(new AccessDeniedException("Only designers are authorized to upload commission drafts."));

        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("MED-01 [B]: Blank fileName rejected -> 400 VALIDATION_FAILED")
    void createSignedUploadUrl_whenBlankFileName_shouldReturn400() throws Exception {
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "   ",
                "image/png",
                102400L,
                MediaPurpose.COMMISSION_REFERENCE,
                null
        );

        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createSignedUploadUrlUseCase);
    }

    @Test
    @DisplayName("MED-01 [B]: SVG+XML MIME type rejected -> 400 VALIDATION_FAILED")
    void createSignedUploadUrl_whenSvgXmlMimeType_shouldReturn400() throws Exception {
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "vector.svg",
                "image/svg+xml",
                102400L,
                MediaPurpose.COMMISSION_REFERENCE,
                null
        );

        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createSignedUploadUrlUseCase);
    }

    @Test
    @DisplayName("MED-01 [B]: Unsupported MIME type rejected -> 400 VALIDATION_FAILED")
    void createSignedUploadUrl_whenUnsupportedMimeType_shouldReturn400() throws Exception {
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "doc.pdf",
                "application/pdf",
                102400L,
                MediaPurpose.COMMISSION_REFERENCE,
                null
        );

        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createSignedUploadUrlUseCase);
    }

    @Test
    @DisplayName("MED-01 [B]: File size <= 0 rejected -> 400 VALIDATION_FAILED")
    void createSignedUploadUrl_whenFileSizeZero_shouldReturn400() throws Exception {
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "empty.png",
                "image/png",
                0L,
                MediaPurpose.COMMISSION_REFERENCE,
                null
        );

        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createSignedUploadUrlUseCase);
    }

    @Test
    @DisplayName("MED-01 [B]: File size > 10MB rejected -> 400 VALIDATION_FAILED")
    void createSignedUploadUrl_whenFileSizeExceeds10Mb_shouldReturn400() throws Exception {
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "huge.png",
                "image/png",
                10_485_761L,
                MediaPurpose.COMMISSION_REFERENCE,
                null
        );

        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createSignedUploadUrlUseCase);
    }

    @Test
    @DisplayName("MED-01 [B]: Null purpose rejected -> 400 VALIDATION_FAILED")
    void createSignedUploadUrl_whenNullPurpose_shouldReturn400() throws Exception {
        String invalidJson = """
                {
                    "fileName": "test.png",
                    "contentType": "image/png",
                    "fileSize": 1024,
                    "purpose": null
                }
                """;

        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createSignedUploadUrlUseCase);
    }

    @Test
    @DisplayName("MED-01 [A]: Storage service failure -> 500 STORAGE_SERVICE_ERROR")
    void createSignedUploadUrl_whenStorageFails_shouldReturn500() throws Exception {
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "test.png",
                "image/png",
                102400L,
                MediaPurpose.COMMISSION_REFERENCE,
                null
        );

        when(createSignedUploadUrlUseCase.execute(any(), any(), any()))
                .thenThrow(new StorageServiceException("Failed to generate signed upload URL"));

        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORAGE_SERVICE_ERROR"));
    }
}
