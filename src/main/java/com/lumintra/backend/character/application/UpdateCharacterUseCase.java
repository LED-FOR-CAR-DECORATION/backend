package com.lumintra.backend.character.application;

import com.lumintra.backend.character.api.request.UpdateCharacterRequest;
import com.lumintra.backend.character.api.response.UpdateCharacterResponse;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.mapper.CharacterMapper;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UpdateCharacterUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateCharacterUseCase.class);
    private static final Set<String> DESIGNER_ROLE = Set.of("DESIGNER");

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final CharacterRepository characterRepository;

    @Transactional
    public UpdateCharacterResponse execute(String subject, Long characterId, UpdateCharacterRequest request) {
        Long callerEmployeeId = internalEmployeeAccessService.requireActiveEmployeeId(subject, DESIGNER_ROLE);

        CharacterEntity character = characterRepository.findById(characterId)
                .orElseThrow(() -> {
                    log.warn("Character update rejected: character not found [characterId={}] [callerEmployeeId={}]",
                            characterId, callerEmployeeId);
                    return new CharacterNotFoundException(characterId);
                });

        if (!character.isOwnedBy(callerEmployeeId)) {
            log.warn("Character update rejected: forbidden ownership mismatch [characterId={}] [creatorEmployeeId={}] [callerEmployeeId={}]",
                    characterId, character.getCreatorEmployeeId(), callerEmployeeId);
            throw new AccessDeniedException("Access is denied.");
        }

        character.update(request.characterName(), request.description());
        CharacterEntity updatedCharacter = characterRepository.save(character);

        log.info("Graphic character updated successfully [characterId={}] [callerEmployeeId={}] [characterName={}]",
                updatedCharacter.getCharacterId(), callerEmployeeId, updatedCharacter.getCharacterName());

        return CharacterMapper.toUpdateResponse(updatedCharacter);
    }
}
