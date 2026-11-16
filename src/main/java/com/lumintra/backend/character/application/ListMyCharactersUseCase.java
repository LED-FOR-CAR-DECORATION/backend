package com.lumintra.backend.character.application;

import com.lumintra.backend.character.api.request.DesignerCharacterFilterRequest;
import com.lumintra.backend.character.api.response.DesignerCharacterItem;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.mapper.DesignerCharacterMapper;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterSpecifications;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListMyCharactersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListMyCharactersUseCase.class);
    private static final Set<String> DESIGNER_ROLE = Set.of("DESIGNER");

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final CharacterRepository characterRepository;
    private final CharacterVersionRepository characterVersionRepository;

    @Transactional(readOnly = true)
    public Page<DesignerCharacterItem> execute(String subject, DesignerCharacterFilterRequest request) {
        Long designerEmployeeId = internalEmployeeAccessService.requireActiveEmployeeId(subject, DESIGNER_ROLE);

        Pageable pageable = PageRequest.of(
                request.page(),
                request.size(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<CharacterEntity> characterPage = characterRepository.findAll(
                CharacterSpecifications.forDesigner(designerEmployeeId, request.query()),
                pageable
        );

        if (characterPage.isEmpty()) {
            log.info("Listed designer characters [designerEmployeeId={}] [query={}] [totalElements=0] [page={}] [size={}]",
                    designerEmployeeId, request.query(), request.page(), request.size());
            return new PageImpl<>(Collections.emptyList(), pageable, characterPage.getTotalElements());
        }

        List<Long> characterIds = characterPage.map(CharacterEntity::getCharacterId).getContent();

        List<CharacterVersionEntity> versions = characterVersionRepository
                .findByCharacter_CharacterIdInOrderByVersionNumberDesc(characterIds);

        Map<Long, List<CharacterVersionEntity>> versionsByCharId = versions.stream()
                .collect(Collectors.groupingBy(v -> v.getCharacter().getCharacterId()));

        List<DesignerCharacterItem> items = characterPage.getContent().stream()
                .map(character -> DesignerCharacterMapper.toItem(
                        character,
                        versionsByCharId.getOrDefault(character.getCharacterId(), Collections.emptyList())
                ))
                .toList();

        log.info("Listed designer characters [designerEmployeeId={}] [query={}] [totalElements={}] [page={}] [size={}]",
                designerEmployeeId, request.query(), characterPage.getTotalElements(), request.page(), request.size());

        return new PageImpl<>(items, pageable, characterPage.getTotalElements());
    }
}
