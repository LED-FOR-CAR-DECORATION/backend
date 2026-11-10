package com.lumintra.backend.character.domain.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface CharacterLookupService {

    Optional<Long> findVersionIdByProductId(Long productId);

    Map<Long, String> findPreviewGifUrlsByProductIds(Collection<Long> productIds);
}
