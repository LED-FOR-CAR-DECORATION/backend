package com.lumintra.backend.character;

import com.lumintra.backend.character.api.controller.UpdateCharacterController;
import com.lumintra.backend.character.api.response.UpdateCharacterResponse;
import com.lumintra.backend.character.application.UpdateCharacterUseCase;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UpdateCharacterController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class UpdateCharacterControllerTest {

    private static final String BASE_PATH = "/api/management/characters/{id}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UpdateCharacterUseCase updateCharacterUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private UpdateCharacterResponse sampleResponse() {
        return new UpdateCharacterResponse(
                12L,
                "Neon Samurai Remastered",
                "Bản nâng cấp màu sắc rực rỡ",
                Instant.parse("2026-11-15T02:10:00Z")
        );
    }

    @Test
    @DisplayName("CHR-04 [N]: DESIGNER updates existing character successfully -> 200 OK")
    void updateCharacter_whenValidRequest_shouldReturnOk() throws Exception {
        when(updateCharacterUseCase.execute(eq("4"), eq(12L), any())).thenReturn(sampleResponse());

        mockMvc.perform(put(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Neon Samurai Remastered",
                                  "description": "Bản nâng cấp màu sắc rực rỡ"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characterId").value(12))
                .andExpect(jsonPath("$.data.characterName").value("Neon Samurai Remastered"))
                .andExpect(jsonPath("$.data.description").value("Bản nâng cấp màu sắc rực rỡ"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-11-15T02:10:00Z"));
    }

    @Test
    @DisplayName("CHR-04 [A]: MANAGER role rejected -> 403 FORBIDDEN")
    void updateCharacter_whenUserIsManager_shouldReturnForbidden() throws Exception {
        mockMvc.perform(put(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("99").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Neon Samurai Remastered"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(updateCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-04 [A]: ADMIN role rejected -> 403 FORBIDDEN")
    void updateCharacter_whenUserIsAdmin_shouldReturnForbidden() throws Exception {
        mockMvc.perform(put(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Neon Samurai Remastered"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(updateCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-04 [A]: STAFF role rejected -> 403 FORBIDDEN")
    void updateCharacter_whenUserIsStaff_shouldReturnForbidden() throws Exception {
        mockMvc.perform(put(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Neon Samurai Remastered"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(updateCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-04 [A]: CUSTOMER role rejected -> 403 FORBIDDEN")
    void updateCharacter_whenUserIsCustomer_shouldReturnForbidden() throws Exception {
        mockMvc.perform(put(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("15").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Neon Samurai Remastered"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(updateCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-04 [A]: Unauthenticated request rejected -> 401 UNAUTHORIZED")
    void updateCharacter_whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(put(BASE_PATH, 12L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Neon Samurai Remastered"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(updateCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-04 [A]: Character does not exist -> 404 CHARACTER_NOT_FOUND")
    void updateCharacter_whenCharacterNotFound_shouldReturnNotFound() throws Exception {
        when(updateCharacterUseCase.execute(eq("4"), eq(999L), any()))
                .thenThrow(new CharacterNotFoundException(999L));

        mockMvc.perform(put(BASE_PATH, 999L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Neon Samurai Remastered"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARACTER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Character not found with id: 999"));
    }

    @Test
    @DisplayName("CHR-04 [A]: Caller is not creator of character (BR-FL5-07) -> 403 FORBIDDEN")
    void updateCharacter_whenNotOwner_shouldReturnForbidden() throws Exception {
        when(updateCharacterUseCase.execute(eq("4"), eq(12L), any()))
                .thenThrow(new AccessDeniedException("Access is denied."));

        mockMvc.perform(put(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Neon Samurai Remastered"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("CHR-04 [B]: Blank characterName rejected -> 400 VALIDATION_FAILED")
    void updateCharacter_whenNameIsBlank_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(put(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(updateCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-04 [B]: characterName exceeding 255 chars rejected -> 400 VALIDATION_FAILED")
    void updateCharacter_whenNameExceeds255Chars_shouldReturnBadRequest() throws Exception {
        String longName = "A".repeat(256);
        mockMvc.perform(put(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "%s"
                                }
                                """.formatted(longName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(updateCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-04 [B]: Non-positive character ID in path rejected -> 400 VALIDATION_FAILED")
    void updateCharacter_whenIdIsNonPositive_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(put(BASE_PATH, 0L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Neon Samurai"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(updateCharacterUseCase);
    }
}
