package com.lumintra.backend.character.domain.service;

import com.lumintra.backend.character.domain.model.CharacterVersionCommissionTarget;
import com.lumintra.backend.character.domain.model.CharacterVersionSnapshot;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface CharacterLookupService {

    Optional<Long> findVersionIdByProductId(Long productId);

    Map<Long, String> findPreviewGifUrlsByProductIds(Collection<Long> productIds);

    Optional<CharacterVersionSnapshot> findDetailByProductId(Long productId);

    Map<Long, CharacterVersionSnapshot> findDetailsByProductIds(Collection<Long> productIds);

    Map<Long, CharacterVersionSnapshot> findDetailsByVersionIds(Collection<Long> versionIds);

    Optional<CharacterVersionCommissionTarget> findCommissionSubmissionTarget(Long versionId);

    void bindCommissionIfUnbound(Long characterId, Long commissionId);
}
