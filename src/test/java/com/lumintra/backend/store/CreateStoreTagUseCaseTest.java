package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.lumintra.backend.shared.exception.store.TagAlreadyExistsException;
import com.lumintra.backend.store.api.request.CreateStoreTagRequest;
import com.lumintra.backend.store.api.response.StoreTagResponse;
import com.lumintra.backend.store.application.CreateStoreTagUseCase;
import com.lumintra.backend.store.domain.event.StoreTagCreatedEvent;
import com.lumintra.backend.store.infrastructure.caching.StoreTagCreatedCacheListener;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagRepository;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;

class CreateStoreTagUseCaseTest {
    private final TagRepository tags = mock(TagRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CreateStoreTagUseCase useCase = new CreateStoreTagUseCase(tags, events);

    @Test
    void locksBeforeCheckingAndSavesTrimmedNameWithDescription() {
        when(tags.save(any()))
                .thenAnswer(
                        invocation -> {
                            TagEntity tag = invocation.getArgument(0);
                            tag.setTagId(7L);
                            return tag;
                        });

        assertThat(useCase.execute(new CreateStoreTagRequest(" Animals ", "Animal art")))
                .isEqualTo(new StoreTagResponse(7L, "Animals", "Animal art"));

        var order = inOrder(tags, events);
        order.verify(tags).lockForCreation();
        order.verify(tags).existsByNormalizedName("Animals");
        order.verify(tags).save(any());
        order.verify(events).publishEvent(new StoreTagCreatedEvent());
    }

    @Test
    void rejectsDuplicateWithoutSavingOrPublishing() {
        when(tags.existsByNormalizedName("ANIMALS")).thenReturn(true);
        assertThatThrownBy(() -> useCase.execute(new CreateStoreTagRequest(" ANIMALS ", null)))
                .isInstanceOf(TagAlreadyExistsException.class);
        verify(tags, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void clearsTagCacheOnCreationEvent() {
        var caches = mock(CacheManager.class);
        var cache = mock(Cache.class);
        when(caches.getCache("store:tags")).thenReturn(cache);
        new StoreTagCreatedCacheListener(caches).afterCommit(new StoreTagCreatedEvent());
        verify(cache).clear();
    }
}
