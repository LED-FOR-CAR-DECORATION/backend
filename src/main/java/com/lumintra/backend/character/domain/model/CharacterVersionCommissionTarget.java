package com.lumintra.backend.character.domain.model;

/**
 * Read-only character version ownership & purpose snapshot for commission draft validation.
 */
public record CharacterVersionCommissionTarget(
        Long versionId,
        Long characterId,
        Long creatorEmployeeId,
        CharacterType characterType,
        Long commissionId
) {}
