package com.lumintra.backend.store.application;

import com.lumintra.backend.store.api.response.StoreTagResponse;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheNames;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListStoreTagsUseCase {
    private final TagRepository tags;

    @Cacheable(cacheNames = StoreCacheNames.TAGS, key = "'all'")
    @Transactional(readOnly = true)
    public StoreTagResponse[] execute() {
        return tags.findAll(Sort.by("name", "tagId")).stream()
                .map(
                        tag ->
                                new StoreTagResponse(
                                        tag.getTagId(), tag.getName(), tag.getDescription()))
                .toArray(StoreTagResponse[]::new);
    }
}
