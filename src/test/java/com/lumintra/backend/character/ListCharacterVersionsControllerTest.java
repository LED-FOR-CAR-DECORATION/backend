package com.lumintra.backend.character;

import com.lumintra.backend.character.api.controller.ListCharacterVersionsController;
import com.lumintra.backend.character.api.response.CharacterVersionResponse;
import com.lumintra.backend.character.application.ListCharacterVersionsUseCase;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ListCharacterVersionsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ListCharacterVersionsControllerTest {

    private static final String BASE_PATH = "/api/management/characters/{id}/versions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListCharacterVersionsUseCase listCharacterVersionsUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("CHR-06 [N]: DESIGNER retrieves own character versions successfully -> 200 OK")
    void listVersions_whenValidDesignerOwner_shouldReturnVersions() throws Exception {
        CharacterVersionResponse v2 = new CharacterVersionResponse(
                25L,
                2,
                "https://cdn.lumintra.com/characters/samurai-v2.gif",
                16,
                100,
                65536,
                true,
                false,
                null,
                null,
                Instant.parse("2026-11-15T02:30:00Z")
        );
        CharacterVersionResponse v1 = new CharacterVersionResponse(
                24L,
                1,
                "https://cdn.lumintra.com/characters/samurai-v1.gif",
                8,
                120,
                32768,
                false,
                true,
                108L,
                "PUBLISHED",
                Instant.parse("2026-11-10T09:00:00Z")
        );

        when(listCharacterVersionsUseCase.execute("4", false, 12L))
                .thenReturn(List.of(v2, v1));

        mockMvc.perform(get(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].versionId").value(25))
                .andExpect(jsonPath("$.data[0].versionNumber").value(2))
                .andExpect(jsonPath("$.data[0].previewGifUrl").value("https://cdn.lumintra.com/characters/samurai-v2.gif"))
                .andExpect(jsonPath("$.data[0].frameCount").value(16))
                .andExpect(jsonPath("$.data[0].frameDelayMs").value(100))
                .andExpect(jsonPath("$.data[0].byteSize").value(65536))
                .andExpect(jsonPath("$.data[0].isCurrent").value(true))
                .andExpect(jsonPath("$.data[0].isListedOnStore").value(false))
                .andExpect(jsonPath("$.data[0].productId").doesNotExist())
                .andExpect(jsonPath("$.data[0].productStatus").doesNotExist())
                .andExpect(jsonPath("$.data[0].ledBinaryUrl").doesNotExist())
                .andExpect(jsonPath("$.data[1].versionId").value(24))
                .andExpect(jsonPath("$.data[1].versionNumber").value(1))
                .andExpect(jsonPath("$.data[1].isListedOnStore").value(true))
                .andExpect(jsonPath("$.data[1].productId").value(108))
                .andExpect(jsonPath("$.data[1].productStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data[1].ledBinaryUrl").doesNotExist());

        verify(listCharacterVersionsUseCase).execute("4", false, 12L);
    }

    @Test
    @DisplayName("CHR-06 [N]: MANAGER retrieves foreign character versions successfully -> 200 OK")
    void listVersions_whenUserIsManager_shouldReturnVersions() throws Exception {
        when(listCharacterVersionsUseCase.execute("88", true, 12L))
                .thenReturn(List.of());

        mockMvc.perform(get(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("88").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(listCharacterVersionsUseCase).execute("88", true, 12L);
    }

    @Test
    @DisplayName("CHR-06 [N]: ADMIN retrieves foreign character versions successfully -> 200 OK")
    void listVersions_whenUserIsAdmin_shouldReturnVersions() throws Exception {
        when(listCharacterVersionsUseCase.execute("1", true, 12L))
                .thenReturn(List.of());

        mockMvc.perform(get(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(listCharacterVersionsUseCase).execute("1", true, 12L);
    }

    @Test
    @DisplayName("CHR-06 [A]: Unauthenticated caller rejected -> 401 UNAUTHORIZED")
    void listVersions_whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get(BASE_PATH, 12L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(listCharacterVersionsUseCase);
    }

    @Test
    @DisplayName("CHR-06 [A]: STAFF role rejected -> 403 FORBIDDEN")
    void listVersions_whenUserIsStaff_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("20").roles("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(listCharacterVersionsUseCase);
    }

    @Test
    @DisplayName("CHR-06 [A]: CUSTOMER role rejected -> 403 FORBIDDEN")
    void listVersions_whenUserIsCustomer_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(listCharacterVersionsUseCase);
    }

    @Test
    @DisplayName("CHR-06 [A]: Character does not exist -> 404 NOT_FOUND")
    void listVersions_whenCharacterNotFound_shouldReturnNotFound() throws Exception {
        when(listCharacterVersionsUseCase.execute("4", false, 999L))
                .thenThrow(new CharacterNotFoundException(999L));

        mockMvc.perform(get(BASE_PATH, 999L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARACTER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Character not found with id: 999"));

        verify(listCharacterVersionsUseCase).execute("4", false, 999L);
    }

    @Test
    @DisplayName("CHR-06 [A]: Designer is not owner of character (BR-FL5-07) -> 403 FORBIDDEN")
    void listVersions_whenDesignerNotOwner_shouldReturnForbidden() throws Exception {
        when(listCharacterVersionsUseCase.execute("4", false, 12L))
                .thenThrow(new AccessDeniedException("Access is denied. You can only inspect versions of characters created by your account."));

        mockMvc.perform(get(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access is denied."));

        verify(listCharacterVersionsUseCase).execute("4", false, 12L);
    }

    @Test
    @DisplayName("CHR-06 [B]: Character ID zero rejected -> 400 BAD_REQUEST")
    void listVersions_whenCharacterIdZero_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get(BASE_PATH, 0L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listCharacterVersionsUseCase);
    }

    @Test
    @DisplayName("CHR-06 [B]: Character ID negative rejected -> 400 BAD_REQUEST")
    void listVersions_whenCharacterIdNegative_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get(BASE_PATH, -5L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listCharacterVersionsUseCase);
    }
}
