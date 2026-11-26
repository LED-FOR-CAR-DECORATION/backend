package com.lumintra.backend.character.application;

import com.lumintra.backend.character.api.request.CreateCharacterRequest;
import com.lumintra.backend.character.api.response.CreateCharacterResponse;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.mapper.CharacterMapper;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class CreateCharacterUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateCharacterUseCase.class);
    private static final Set<String> DESIGNER_ROLE = Set.of("DESIGNER");

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final CharacterRepository characterRepository;

    @Transactional
    public CreateCharacterResponse execute(String subject, CreateCharacterRequest request) {
        Long designerEmployeeId = internalEmployeeAccessService.requireActiveEmployeeId(subject, DESIGNER_ROLE);

        CharacterEntity entity = CharacterEntity.create(
                designerEmployeeId,
                request.characterName(),
                request.description(),
                request.characterType(),
                request.commissionId()
        );

        CharacterEntity saved = characterRepository.save(entity);

        log.info("Created new character [characterId={}] [characterName={}] [characterType={}] [commissionId={}] [creatorEmployeeId={}]",
                saved.getCharacterId(), saved.getCharacterName(), saved.getCharacterType(), saved.getCommissionId(), designerEmployeeId);

        return CharacterMapper.toCreateResponse(saved);
    }
}
