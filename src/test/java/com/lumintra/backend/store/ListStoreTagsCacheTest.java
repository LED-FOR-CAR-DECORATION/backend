package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.lumintra.backend.shared.caching.RedisConfig;
import com.lumintra.backend.store.api.response.StoreTagResponse;
import com.lumintra.backend.store.application.ListStoreTagsUseCase;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.List;

@SpringJUnitConfig({RedisConfig.class, ListStoreTagsUseCase.class})
class ListStoreTagsCacheTest {
    @Autowired ListStoreTagsUseCase useCase;
    @Autowired CacheManager cacheManager;
    @MockitoBean RedisConnectionFactory connections;
    @MockitoBean TagRepository tags;

    @Test
    void returnsDatabaseTagsWhenRedisReadAndWriteFail() {
        when(connections.getConnection()).thenThrow(new IllegalStateException("Redis unavailable"));
        var sort = Sort.by("name", "tagId");
        when(tags.findAll(sort))
                .thenReturn(
                        List.of(
                                TagEntity.of(7L, "Animals", null),
                                TagEntity.of(2L, "Space", "Space art")));

        assertThat(useCase.execute())
                .containsExactly(
                        new StoreTagResponse(7L, "Animals", null),
                        new StoreTagResponse(2L, "Space", "Space art"));
        verify(tags).findAll(sort);
    }

    @Test
    void usesSixtyMinuteTtlAndRoundTripsTagArrays() {
        Cache cache = cacheManager.getCache("store:tags");
        var config = ((RedisCache) cache).getCacheConfiguration();
        assertThat(config.getTtlFunction().getTimeToLive("all", null))
                .isEqualTo(Duration.ofMinutes(60));
        assertThat(config.getKeyPrefixFor("store:tags")).isEqualTo("store:tags:");
        var serialization = config.getValueSerializationPair();
        var payload = new StoreTagResponse[] {new StoreTagResponse(7L, "Animals", null)};
        assertThat(serialization.read(serialization.write(payload))).isEqualTo(payload);
        assertThat(serialization.read(serialization.write(new StoreTagResponse[0])))
                .isEqualTo(new StoreTagResponse[0]);
    }
}
