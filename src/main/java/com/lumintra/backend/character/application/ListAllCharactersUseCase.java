package com.lumintra.backend.character.application;

import com.lumintra.backend.character.api.request.AdminCharacterFilterRequest;
import com.lumintra.backend.character.api.response.AdminCharacterItem;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.mapper.AdminCharacterMapper;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterSpecifications;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListAllCharactersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListAllCharactersUseCase.class);

    private final CharacterRepository characterRepository;
    private final CharacterVersionRepository characterVersionRepository;
    private final EmployeeLookupService employeeLookupService;

    @Transactional(readOnly = true)
    public Page<AdminCharacterItem> execute(AdminCharacterFilterRequest request) {
        Pageable pageable = PageRequest.of(
                request.page(),
                request.size(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<CharacterEntity> characterPage = characterRepository.findAll(
                CharacterSpecifications.forAdmin(request.query(), request.creatorEmployeeId()),
                pageable
        );

        if (characterPage.isEmpty()) {
            log.info("Listed admin characters [query={}] [creatorEmployeeId={}] [totalElements=0] [page={}] [size={}]",
                    request.query(), request.creatorEmployeeId(), request.page(), request.size());
            return new PageImpl<>(Collections.emptyList(), pageable, characterPage.getTotalElements());
        }

        List<Long> characterIds = characterPage.map(CharacterEntity::getCharacterId).getContent();

        Map<Long, Integer> versionCounts = characterVersionRepository.countVersionsByCharacterIds(characterIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).intValue(),
                        (existing, replacement) -> existing
                ));

        Set<Long> authorEmployeeIds = characterPage.getContent().stream()
                .map(CharacterEntity::getCreatorEmployeeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, EmployeeProfileSnapshot> authors = employeeLookupService.findProfilesByEmployeeIds(authorEmployeeIds);

        List<AdminCharacterItem> items = characterPage.getContent().stream()
                .map(character -> AdminCharacterMapper.toItem(
                        character,
                        character.getCreatorEmployeeId() != null ? authors.get(character.getCreatorEmployeeId()) : null,
                        versionCounts.getOrDefault(character.getCharacterId(), 0)
                ))
                .toList();

        log.info("Listed admin characters [query={}] [creatorEmployeeId={}] [totalElements={}] [page={}] [size={}]",
                request.query(), request.creatorEmployeeId(), characterPage.getTotalElements(), request.page(), request.size());

        return new PageImpl<>(items, pageable, characterPage.getTotalElements());
    }
}
