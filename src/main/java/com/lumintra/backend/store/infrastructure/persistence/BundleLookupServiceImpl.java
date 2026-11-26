package com.lumintra.backend.store.infrastructure.persistence;

import com.lumintra.backend.store.domain.model.BundleSnapshot;
import com.lumintra.backend.store.domain.service.BundleLookupService;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleItemEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BundleLookupServiceImpl implements BundleLookupService {

    private final BundleRepository bundleRepository;
    private final BundleItemRepository bundleItemRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<BundleSnapshot> findByProductId(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }

        return bundleRepository.findByProductId(productId)
                .map(bundle -> {
                    List<BundleSnapshot.BundleItemSnapshot> itemSnapshots = bundleItemRepository
                            .findAllByBundle_BundleId(bundle.getBundleId())
                            .stream()
                            .map(this::toItemSnapshot)
                            .toList();

                    return new BundleSnapshot(
                            bundle.getBundleId(),
                            bundle.getProductId(),
                            bundle.getBundleName(),
                            bundle.getBundlePrice(),
                            bundle.getStatus(),
                            bundle.getStartAt(),
                            bundle.getEndAt(),
                            itemSnapshots
                    );
                });
    }

    private BundleSnapshot.BundleItemSnapshot toItemSnapshot(BundleItemEntity entity) {
        return new BundleSnapshot.BundleItemSnapshot(
                entity.getBundleItemId(),
                entity.getItemType(),
                entity.getModelId(),
                entity.getVersionId(),
                entity.getQuantity()
        );
    }
}
