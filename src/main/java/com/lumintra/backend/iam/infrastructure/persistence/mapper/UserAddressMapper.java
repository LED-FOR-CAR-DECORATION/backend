package com.lumintra.backend.iam.infrastructure.persistence.mapper;

import com.lumintra.backend.iam.api.response.UserAddressResponse;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAddressEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class UserAddressMapper {

    public List<UserAddressResponse> toResponseList(List<UserAddressEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(this::toResponse)
                .toList();
    }

    public UserAddressResponse toResponse(UserAddressEntity entity) {
        if (entity == null) {
            return null;
        }
        return new UserAddressResponse(
                entity.getAddressId(),
                entity.getRecipientName(),
                entity.getRecipientPhone(),
                entity.getFullAddress(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.isDefault(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
