package com.lumintra.backend.device.application;

import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.device.LedModelNotFoundException;
import com.lumintra.backend.store.domain.event.StoreProductUpdatedEvent;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeprecateLedModelUseCaseTest {

    @Mock
    private LedModelRepository ledModelRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DeprecateLedModelUseCase useCase;

    private LedModelEntity createSampleModel(Long modelId, Long productId, LedModelStatus status) {
        return LedModelEntity.createCatalogModel(
                modelId,
                productId,
                "Lumintra Matrix 64x64",
                64,
                64,
                Map.of("widthPx", 64, "heightPx", 64),
                10L,
                status
        );
    }

    private ProductEntity createSampleProduct(Long productId, ProductStatus status) {
        return ProductEntity.of(
                productId,
                "Lumintra LED Matrix Pro",
                ProductType.LED,
                BigDecimal.valueOf(1500000),
                "Premium LED Display",
                "https://cdn.lumintra.shop/thumb.png",
                List.of(),
                status,
                Set.of(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("DEV-04 UTCID01 [N]: Deprecate standalone model (no linked store product) transitions model to DEPRECATED")
    void execute_standaloneModel_deprecatesModelSuccessfully() {
        Long modelId = 1L;
        LedModelEntity model = createSampleModel(modelId, null, LedModelStatus.ACTIVE);

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(modelId);

        assertThat(model.getStatus()).isEqualTo(LedModelStatus.DEPRECATED);
        verify(ledModelRepository, times(1)).save(model);
        verify(productRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("DEV-04 UTCID02 [N]: Deprecate model with linked PUBLISHED product transitions model to DEPRECATED, product to ARCHIVED, and publishes cache event")
    void execute_modelWithPublishedProduct_deprecatesModelAndArchivesProductAndPublishesEvent() {
        Long modelId = 2L;
        Long productId = 100L;
        LedModelEntity model = createSampleModel(modelId, productId, LedModelStatus.ACTIVE);
        ProductEntity product = createSampleProduct(productId, ProductStatus.PUBLISHED);

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(modelId);

        assertThat(model.getStatus()).isEqualTo(LedModelStatus.DEPRECATED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ARCHIVED);

        verify(ledModelRepository, times(1)).save(model);
        verify(productRepository, times(1)).save(product);

        ArgumentCaptor<StoreProductUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(StoreProductUpdatedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().productId()).isEqualTo(productId);
    }

    @Test
    @DisplayName("DEV-04 UTCID03 [N]: Deprecate model with linked UNPUBLISHED or ARCHIVED product does not change product status or publish cache event")
    void execute_modelWithUnpublishedProduct_deprecatesModelWithoutChangingProductOrPublishingEvent() {
        Long modelId = 3L;
        Long productId = 101L;
        LedModelEntity model = createSampleModel(modelId, productId, LedModelStatus.ACTIVE);
        ProductEntity product = createSampleProduct(productId, ProductStatus.UNPUBLISHED);

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        useCase.execute(modelId);

        assertThat(model.getStatus()).isEqualTo(LedModelStatus.DEPRECATED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.UNPUBLISHED);

        verify(ledModelRepository, times(1)).save(model);
        verify(productRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("DEV-04 UTCID04 [N]: Idempotency: deprecating an already DEPRECATED model keeps status DEPRECATED and persists")
    void execute_alreadyDeprecatedModel_isIdempotent() {
        Long modelId = 4L;
        LedModelEntity model = createSampleModel(modelId, null, LedModelStatus.DEPRECATED);

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(modelId);

        assertThat(model.getStatus()).isEqualTo(LedModelStatus.DEPRECATED);
        verify(ledModelRepository, times(1)).save(model);
        verify(productRepository, never()).findById(any());
    }

    @Test
    @DisplayName("DEV-04 UTCID05 [A]: Deprecating non-existent modelId throws LedModelNotFoundException (404)")
    void execute_modelNotFound_throwsLedModelNotFoundException() {
        Long nonExistentId = 999L;
        when(ledModelRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(nonExistentId))
                .isInstanceOf(LedModelNotFoundException.class)
                .hasMessage("LED model with id 999 not found.");

        verify(ledModelRepository, never()).save(any());
        verify(productRepository, never()).findById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("DEV-04 UTCID06 [N]: Model has linked productId but product is not found in database safely deprecates model without error")
    void execute_linkedProductNotFoundInRepo_deprecatesModelSafely() {
        Long modelId = 5L;
        Long orphanProductId = 200L;
        LedModelEntity model = createSampleModel(modelId, orphanProductId, LedModelStatus.ACTIVE);

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findById(orphanProductId)).thenReturn(Optional.empty());

        useCase.execute(modelId);

        assertThat(model.getStatus()).isEqualTo(LedModelStatus.DEPRECATED);
        verify(ledModelRepository, times(1)).save(model);
        verify(productRepository, times(1)).findById(orphanProductId);
        verify(productRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
