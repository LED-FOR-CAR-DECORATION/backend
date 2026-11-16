package com.lumintra.backend.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.character.api.controller.CreateCharacterVersionController;
import com.lumintra.backend.character.api.request.CreateCharacterVersionRequest;
import com.lumintra.backend.character.api.response.CreateCharacterVersionResponse;
import com.lumintra.backend.character.application.CreateCharacterVersionUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CreateCharacterVersionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CreateCharacterVersionControllerTest {

    private static final String BASE_PATH = "/api/management/characters/{id}/versions";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CreateCharacterVersionUseCase createCharacterVersionUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private CreateCharacterVersionRequest validRequest() {
        return new CreateCharacterVersionRequest(
                "https://cdn.lumintra.com/characters/samurai-v2.gif",
                16,
                100,
                65536,
                true
        );
    }

    @Test
    @DisplayName("CHR-07 [N]: DESIGNER creates version for own character -> 201 Created")
    void createVersion_whenValidRequest_shouldReturnCreated() throws Exception {
        CreateCharacterVersionResponse response = new CreateCharacterVersionResponse(
                25L,
                12L,
                2,
                "https://cdn.lumintra.com/characters/samurai-v2.gif",
                true,
                false,
                Instant.parse("2026-11-15T02:30:00Z")
        );

        when(createCharacterVersionUseCase.execute(eq("4"), eq(12L), any(CreateCharacterVersionRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.versionId").value(25))
                .andExpect(jsonPath("$.data.characterId").value(12))
                .andExpect(jsonPath("$.data.versionNumber").value(2))
                .andExpect(jsonPath("$.data.previewGifUrl").value("https://cdn.lumintra.com/characters/samurai-v2.gif"))
                .andExpect(jsonPath("$.data.isCurrent").value(true))
                .andExpect(jsonPath("$.data.isListedOnStore").value(false));

        verify(createCharacterVersionUseCase).execute(eq("4"), eq(12L), any(CreateCharacterVersionRequest.class));
    }

    @Test
    @DisplayName("CHR-07 [A]: Unauthenticated caller rejected -> 401 UNAUTHORIZED")
    void createVersion_whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post(BASE_PATH, 12L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(createCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-07 [A]: MANAGER role rejected -> 403 FORBIDDEN")
    void createVersion_whenUserIsManager_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("88").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(createCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-07 [A]: ADMIN role rejected -> 403 FORBIDDEN")
    void createVersion_whenUserIsAdmin_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(createCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-07 [A]: STAFF role rejected -> 403 FORBIDDEN")
    void createVersion_whenUserIsStaff_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("20").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(createCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-07 [A]: CUSTOMER role rejected -> 403 FORBIDDEN")
    void createVersion_whenUserIsCustomer_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(createCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-07 [A]: Character does not exist -> 404 NOT_FOUND")
    void createVersion_whenCharacterNotFound_shouldReturnNotFound() throws Exception {
        when(createCharacterVersionUseCase.execute(eq("4"), eq(999L), any(CreateCharacterVersionRequest.class)))
                .thenThrow(new CharacterNotFoundException(999L));

        mockMvc.perform(post(BASE_PATH, 999L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARACTER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Character not found with id: 999"));

        verify(createCharacterVersionUseCase).execute(eq("4"), eq(999L), any(CreateCharacterVersionRequest.class));
    }

    @Test
    @DisplayName("CHR-07 [A]: Designer is not owner of character (BR-FL5-07) -> 403 FORBIDDEN")
    void createVersion_whenDesignerNotOwner_shouldReturnForbidden() throws Exception {
        when(createCharacterVersionUseCase.execute(eq("4"), eq(12L), any(CreateCharacterVersionRequest.class)))
                .thenThrow(new AccessDeniedException("Access is denied. You can only create versions for your own characters."));

        mockMvc.perform(post(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access is denied."));

        verify(createCharacterVersionUseCase).execute(eq("4"), eq(12L), any(CreateCharacterVersionRequest.class));
    }

    @Test
    @DisplayName("CHR-07 [B]: Character ID zero rejected -> 400 BAD_REQUEST")
    void createVersion_whenCharacterIdZero_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post(BASE_PATH, 0L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-07 [B]: Blank previewGifUrl rejected -> 400 BAD_REQUEST")
    void createVersion_whenBlankPreviewUrl_shouldReturnBadRequest() throws Exception {
        CreateCharacterVersionRequest invalid = new CreateCharacterVersionRequest(
                "",
                16,
                100,
                65536,
                true
        );

        mockMvc.perform(post(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-07 [B]: Invalid URL format for previewGifUrl rejected -> 400 BAD_REQUEST")
    void createVersion_whenInvalidUrlFormat_shouldReturnBadRequest() throws Exception {
        CreateCharacterVersionRequest invalid = new CreateCharacterVersionRequest(
                "not-a-valid-url",
                16,
                100,
                65536,
                true
        );

        mockMvc.perform(post(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-07 [B]: Zero frameCount rejected -> 400 BAD_REQUEST")
    void createVersion_whenZeroFrameCount_shouldReturnBadRequest() throws Exception {
        CreateCharacterVersionRequest invalid = new CreateCharacterVersionRequest(
                "https://cdn.lumintra.com/characters/samurai-v2.gif",
                0,
                100,
                65536,
                true
        );

        mockMvc.perform(post(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-07 [B]: Negative frameDelayMs rejected -> 400 BAD_REQUEST")
    void createVersion_whenNegativeFrameDelay_shouldReturnBadRequest() throws Exception {
        CreateCharacterVersionRequest invalid = new CreateCharacterVersionRequest(
                "https://cdn.lumintra.com/characters/samurai-v2.gif",
                16,
                -10,
                65536,
                true
        );

        mockMvc.perform(post(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-07 [B]: Zero byteSize rejected -> 400 BAD_REQUEST")
    void createVersion_whenZeroByteSize_shouldReturnBadRequest() throws Exception {
        CreateCharacterVersionRequest invalid = new CreateCharacterVersionRequest(
                "https://cdn.lumintra.com/characters/samurai-v2.gif",
                16,
                100,
                0,
                true
        );

        mockMvc.perform(post(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createCharacterVersionUseCase);
    }
}
