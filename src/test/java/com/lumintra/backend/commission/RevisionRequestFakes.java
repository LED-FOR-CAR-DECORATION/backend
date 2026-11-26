package com.lumintra.backend.commission;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.lumintra.backend.commission.application.service.CommissionRevisionRequestRecorder;
import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRevisionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRevisionRequestRepository;

import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** In-memory {@link CommissionRevisionRequestRepository} for unit tests. */
public final class RevisionRequestFakes {
    public final List<CommissionRevisionRequestEntity> store = new ArrayList<>();
    public final CommissionRevisionRequestRepository repository;
    public final CommissionRevisionRequestRecorder recorder;

    public RevisionRequestFakes() {
        this(
                mock(
                        CommissionRevisionRequestRepository.class,
                        withSettings().strictness(Strictness.LENIENT)));
    }

    /** Backs an existing (lenient) Mockito mock with the in-memory store. */
    @SuppressWarnings("unchecked")
    public RevisionRequestFakes(CommissionRevisionRequestRepository repository) {
        this.repository = repository;
        this.recorder = new CommissionRevisionRequestRecorder(repository);
        when(repository.save(any()))
                .thenAnswer(
                        inv -> {
                            CommissionRevisionRequestEntity entity = inv.getArgument(0);
                            if (entity.getRevisionRequestId() == null) {
                                entity.setRevisionRequestId((long) store.size() + 1);
                                store.add(entity);
                            }
                            return entity;
                        });
        when(repository.findById(any()))
                .thenAnswer(
                        inv ->
                                store.stream()
                                        .filter(r -> Objects.equals(r.getRevisionRequestId(), inv.getArgument(0)))
                                        .findFirst());
        when(repository.findByMessageId(any()))
                .thenAnswer(
                        inv ->
                                store.stream()
                                        .filter(r -> Objects.equals(r.getMessageId(), inv.getArgument(0)))
                                        .findFirst());
        when(repository.findAllByCommissionIdAndStatus(any(), any()))
                .thenAnswer(
                        inv ->
                                store.stream()
                                        .filter(r -> Objects.equals(r.getCommissionId(), inv.getArgument(0)))
                                        .filter(r -> r.getStatus() == inv.getArgument(1))
                                        .toList());
        when(repository.findFirstByCommissionIdAndStatusInOrderByRevisionRequestIdDesc(any(), any()))
                .thenAnswer(
                        inv ->
                                store.stream()
                                        .filter(r -> Objects.equals(r.getCommissionId(), inv.getArgument(0)))
                                        .filter(r -> ((Collection<CommissionRevisionRequestStatus>) inv.getArgument(1))
                                                .contains(r.getStatus()))
                                        .max(Comparator.comparing(CommissionRevisionRequestEntity::getRevisionRequestId)));
        when(repository.findAllByCommissionIdAndStatusNotOrderByRevisionRequestIdAsc(any(), any()))
                .thenAnswer(
                        inv ->
                                store.stream()
                                        .filter(r -> Objects.equals(r.getCommissionId(), inv.getArgument(0)))
                                        .filter(r -> r.getStatus() != inv.getArgument(1))
                                        .toList());
    }

    public CommissionRevisionRequestEntity only() {
        if (store.size() != 1) {
            throw new AssertionError("Expected exactly one revision request but found " + store.size());
        }
        return store.get(0);
    }
}
