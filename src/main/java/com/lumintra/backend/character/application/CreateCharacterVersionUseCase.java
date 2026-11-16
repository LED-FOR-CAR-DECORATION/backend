package com.lumintra.backend.character.application;

import com.lumintra.backend.character.api.request.CreateCharacterVersionRequest;
import com.lumintra.backend.character.api.response.CreateCharacterVersionResponse;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.mapper.CharacterVersionMapper;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CreateCharacterVersionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateCharacterVersionUseCase.class);
    private static final Set<String> DESIGNER_ROLE = Set.of("DESIGNER");

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final CharacterRepository characterRepository;
    private final CharacterVersionRepository characterVersionRepository;
    private final CharacterVersionMapper characterVersionMapper;

    @Transactional
    public CreateCharacterVersionResponse execute(String subject, Long characterId, CreateCharacterVersionRequest request) {
        Long designerEmployeeId = internalEmployeeAccessService.requireActiveEmployeeId(subject, DESIGNER_ROLE);

        CharacterEntity character = characterRepository.findById(characterId)
                .orElseThrow(() -> {
                    log.warn("Create character version rejected: character not found [characterId={}] [callerEmployeeId={}]",
                            characterId, designerEmployeeId);
                    return new CharacterNotFoundException(characterId);
                });

        if (!character.isOwnedBy(designerEmployeeId)) {
            log.warn("Create character version rejected: forbidden ownership mismatch [characterId={}] [creatorEmployeeId={}] [callerEmployeeId={}]",
                    characterId, character.getCreatorEmployeeId(), designerEmployeeId);
            throw new AccessDeniedException("Access is denied. You can only create versions for your own characters.");
        }

        List<CharacterVersionEntity> existingVersions = characterVersionRepository
                .findByCharacter_CharacterIdOrderByVersionNumberDesc(characterId);

        int nextVersionNumber = existingVersions.isEmpty()
                ? 1
                : existingVersions.getFirst().getVersionNumber() + 1;

        boolean isCurrent = Boolean.TRUE.equals(request.isCurrent());

        if (isCurrent) {
            for (CharacterVersionEntity prev : existingVersions) {
                if (prev.isCurrent()) {
                    prev.setCurrent(false);
                }
            }
        }

        CharacterVersionEntity newVersion = CharacterVersionEntity.create(
                character,
                null,
                nextVersionNumber,
                request.previewGifUrl(),
                request.frameCount(),
                request.frameDelayMs(),
                request.byteSize(),
                isCurrent
        );

        CharacterVersionEntity saved = characterVersionRepository.save(newVersion);

        log.info("Character version created successfully [characterId={}] [versionId={}] [versionNumber={}] [isCurrent={}] [creatorEmployeeId={}]",
                characterId, saved.getVersionId(), saved.getVersionNumber(), isCurrent, designerEmployeeId);

        return characterVersionMapper.toCreateResponse(saved);
    }
}
