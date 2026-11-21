package com.lumintra.backend.character.domain.service;

public interface CharacterProductLinkService {
    void linkVersion(long versionId, long productId);
    void linkOwnedVersion(long versionId, long productId, long designerEmployeeId);
}
