package com.lumintra.backend.character.domain.service;

import java.util.Optional;

public interface CharacterLookupService {

    Optional<Long> findVersionIdByProductId(Long productId);
}
