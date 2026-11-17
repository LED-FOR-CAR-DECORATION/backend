package com.lumintra.backend.store.application;

import com.lumintra.backend.shared.exception.store.BundleNotFoundException;
import com.lumintra.backend.store.api.request.CreateBundleRequest;
import com.lumintra.backend.store.api.response.CreatedBundleResponse;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetBundleDetailUseCase {
    private final BundleRepository bundles;
    private final ProductRepository products;
    private final BundleItemRepository bundleItems;

    @Transactional
    public CreatedBundleResponse execute(long id) {
        var bundle = bundles.findById(id)
                .or(() -> bundles.findByProductId(id))
                .orElseThrow(BundleNotFoundException::new);

        var product = products.findWithTagsByProductId(bundle.getProductId())
                .or(() -> products.findById(bundle.getProductId()))
                .orElse(null);

        var items = bundleItems.findAllByBundle_BundleIdOrderByBundleItemIdAsc(bundle.getBundleId()).stream()
                .map(item -> new CreateBundleRequest.Item(
                        item.getModelId(),
                        item.getVersionId(),
                        item.getQuantity()))
                .toList();

        String thumbnailUrl = product != null ? product.getThumbnailUrl() : null;
        List<String> galleryUrls = new ArrayList<>();
        if (product != null && product.getGalleryUrls() != null) {
            for (String url : product.getGalleryUrls()) {
                if (url != null && !url.isBlank()) {
                    galleryUrls.add(url);
                }
            }
        }

        List<Long> tagIds = new ArrayList<>();
        if (product != null && product.getTags() != null) {
            try {
                for (TagEntity tag : product.getTags()) {
                    if (tag != null && tag.getTagId() != null) {
                        tagIds.add(tag.getTagId());
                    }
                }
                tagIds.sort(Long::compareTo);
            } catch (Exception ignored) {
                // Ignore lazy initialization exceptions gracefully
            }
        }

        return new CreatedBundleResponse(
                bundle.getBundleId(),
                product != null ? product.getProductId() : bundle.getProductId(),
                bundle.getBundleName(),
                bundle.getDescription(),
                thumbnailUrl,
                galleryUrls,
                bundle.getOriginalPrice(),
                bundle.getBundlePrice(),
                bundle.getStatus(),
                tagIds,
                items);
    }
}
