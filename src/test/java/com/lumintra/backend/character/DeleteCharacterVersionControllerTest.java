package com.lumintra.backend.character;

import com.lumintra.backend.character.api.controller.DeleteCharacterVersionController;
import com.lumintra.backend.character.application.DeleteCharacterVersionUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
import com.lumintra.backend.shared.exception.character.CharacterVersionAlreadyLinkedToProductException;
import com.lumintra.backend.shared.exception.character.CharacterVersionNotFoundException;
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

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeleteCharacterVersionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class DeleteCharacterVersionControllerTest {

    private static final String BASE_PATH = "/api/management/characters/{characterId}/versions/{versionId}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeleteCharacterVersionUseCase deleteCharacterVersionUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("CHR-08 [N]: DESIGNER deletes unlisted version successfully -> 204 No Content")
    void deleteVersion_whenValidDraftVersion_shouldReturnNoContent() throws Exception {
        doNothing().when(deleteCharacterVersionUseCase).execute("4", 12L, 25L);

        mockMvc.perform(delete(BASE_PATH, 12L, 25L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isNoContent());

        verify(deleteCharacterVersionUseCase).execute("4", 12L, 25L);
    }

    @Test
    @DisplayName("CHR-08 [A]: Unauthenticated request rejected -> 401 UNAUTHORIZED")
    void deleteVersion_whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(delete(BASE_PATH, 12L, 25L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(deleteCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-08 [A]: MANAGER role rejected -> 403 FORBIDDEN")
    void deleteVersion_whenUserIsManager_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete(BASE_PATH, 12L, 25L)
                        .with(SecurityMockMvcRequestPostProcessors.user("99").roles("MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(deleteCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-08 [A]: ADMIN role rejected -> 403 FORBIDDEN")
    void deleteVersion_whenUserIsAdmin_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete(BASE_PATH, 12L, 25L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(deleteCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-08 [A]: STAFF role rejected -> 403 FORBIDDEN")
    void deleteVersion_whenUserIsStaff_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete(BASE_PATH, 12L, 25L)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(deleteCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-08 [A]: CUSTOMER role rejected -> 403 FORBIDDEN")
    void deleteVersion_whenUserIsCustomer_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete(BASE_PATH, 12L, 25L)
                        .with(SecurityMockMvcRequestPostProcessors.user("15").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(deleteCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-08 [A]: Character does not exist -> 404 CHARACTER_NOT_FOUND")
    void deleteVersion_whenCharacterNotFound_shouldReturnNotFound() throws Exception {
        doThrow(new CharacterNotFoundException(999L))
                .when(deleteCharacterVersionUseCase).execute("4", 999L, 25L);

        mockMvc.perform(delete(BASE_PATH, 999L, 25L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARACTER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Character not found with id: 999"));
    }

    @Test
    @DisplayName("CHR-08 [A]: Caller is not creator of character (BR-FL5-07) -> 403 FORBIDDEN")
    void deleteVersion_whenNotOwner_shouldReturnForbidden() throws Exception {
        doThrow(new AccessDeniedException("Access is denied."))
                .when(deleteCharacterVersionUseCase).execute("4", 12L, 25L);

        mockMvc.perform(delete(BASE_PATH, 12L, 25L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("CHR-08 [A]: Character version does not exist -> 404 VERSION_NOT_FOUND")
    void deleteVersion_whenVersionNotFound_shouldReturnNotFound() throws Exception {
        doThrow(new CharacterVersionNotFoundException(12L, 999L))
                .when(deleteCharacterVersionUseCase).execute("4", 12L, 999L);

        mockMvc.perform(delete(BASE_PATH, 12L, 999L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VERSION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Character version with id 999 was not found for character 12"));
    }

    @Test
    @DisplayName("CHR-08 [A]: Version listed on Store (BR-FL5-09) -> 409 VERSION_ALREADY_LINKED_TO_PRODUCT")
    void deleteVersion_whenVersionListedOnStore_shouldReturnConflict() throws Exception {
        doThrow(new CharacterVersionAlreadyLinkedToProductException(25L, 108L))
                .when(deleteCharacterVersionUseCase).execute("4", 12L, 25L);

        mockMvc.perform(delete(BASE_PATH, 12L, 25L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_ALREADY_LINKED_TO_PRODUCT"))
                .andExpect(jsonPath("$.message").value("Cannot delete character version with id 25 because it is listed on the Store (productId: 108)."));
    }

    @Test
    @DisplayName("CHR-08 [B]: Non-positive characterId in path rejected -> 400 VALIDATION_FAILED")
    void deleteVersion_whenCharacterIdIsNonPositive_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(delete(BASE_PATH, 0L, 25L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(deleteCharacterVersionUseCase);
    }

    @Test
    @DisplayName("CHR-08 [B]: Non-positive versionId in path rejected -> 400 VALIDATION_FAILED")
    void deleteVersion_whenVersionIdIsNonPositive_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(delete(BASE_PATH, 12L, 0L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(deleteCharacterVersionUseCase);
    }
}
