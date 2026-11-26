package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.lumintra.backend.shared.exception.store.StoreProductTagNotFoundException;
import com.lumintra.backend.shared.exception.store.TagAlreadyExistsException;
import com.lumintra.backend.store.api.request.UpdateStoreTagRequest;
import com.lumintra.backend.store.api.response.StoreTagResponse;
import com.lumintra.backend.store.application.UpdateStoreTagUseCase;
import com.lumintra.backend.store.domain.event.StoreTagUpdatedEvent;
import com.lumintra.backend.store.infrastructure.caching.StoreTagUpdatedCacheListener;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagRepository;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

class UpdateStoreTagUseCaseTest {
    private final TagRepository tags = mock(TagRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UpdateStoreTagUseCase useCase = new UpdateStoreTagUseCase(tags, events);

    @Test
    void updatesNameAndClearsOmittedDescriptionWithCurrentIdExcluded() {
        var tag = TagEntity.of(7L, "Animals", "Old description");
        when(tags.findById(7L)).thenReturn(Optional.of(tag));
        assertThat(useCase.execute(7L, new UpdateStoreTagRequest(" ANIMALS ", null)))
                .isEqualTo(new StoreTagResponse(7L, "ANIMALS", null));
        var order = inOrder(tags, events);
        order.verify(tags).lockForCreation();
        order.verify(tags).findById(7L);
        order.verify(tags).existsByNormalizedNameExcluding("ANIMALS", 7L);
        order.verify(events).publishEvent(new StoreTagUpdatedEvent());
        assertThat(tag.getDescription()).isNull();
    }

    @Test
    void rejectsMissingAndConflictingTagsWithoutPublishing() {
        var tag = TagEntity.of(7L, "Animals", "Old description");
        when(tags.findById(7L)).thenReturn(Optional.of(tag));
        when(tags.existsByNormalizedNameExcluding("Space", 7L)).thenReturn(true);
        var request = new UpdateStoreTagRequest("Space", null);

        assertThatThrownBy(() -> useCase.execute(8L, request))
                .isInstanceOf(StoreProductTagNotFoundException.class);
        assertThatThrownBy(() -> useCase.execute(7L, request))
                .isInstanceOf(TagAlreadyExistsException.class);
        assertThat(tag.getName()).isEqualTo("Animals");
        verifyNoInteractions(events);
    }

    @Test
    void attemptsAllCacheClearsEvenIfTagCacheFails() {
        var caches = mock(CacheManager.class);
        var tagsCache = mock(Cache.class);
        var listCache = mock(Cache.class);
        var detailCache = mock(Cache.class);
        when(caches.getCache("store:tags")).thenReturn(tagsCache);
        when(caches.getCache("store:products")).thenReturn(listCache);
        when(caches.getCache("store:product")).thenReturn(detailCache);
        doThrow(new IllegalStateException("Redis unavailable")).when(tagsCache).clear();

        new StoreTagUpdatedCacheListener(caches).afterCommit(new StoreTagUpdatedEvent());

        verify(tagsCache).clear();
        verify(listCache).clear();
        verify(detailCache).clear();
    }
}
