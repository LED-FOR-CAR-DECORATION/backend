package com.lumintra.backend.iam.infrastructure.persistence;

import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeLookupServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;

    private EmployeeLookupServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmployeeLookupServiceImpl(employeeRepository);
    }

    @Test
    @DisplayName("CHR-02 IAM UTCID01 [N]: findProfilesByEmployeeIds returns map of employee snapshots")
    void findProfilesByEmployeeIds_validIds_returnsMap() {
        EmployeeProfileSnapshot emp1 = new EmployeeProfileSnapshot(4L, "Test Designer", "designer@lumintra.com");
        EmployeeProfileSnapshot emp2 = new EmployeeProfileSnapshot(5L, "Artist Two", "artist@lumintra.com");

        when(employeeRepository.findProfileSnapshotsByEmployeeIds(anyCollection()))
                .thenReturn(List.of(emp1, emp2));

        Map<Long, EmployeeProfileSnapshot> result = service.findProfilesByEmployeeIds(List.of(4L, 5L));

        assertThat(result).hasSize(2);
        assertThat(result.get(4L)).isEqualTo(emp1);
        assertThat(result.get(5L)).isEqualTo(emp2);
    }

    @Test
    @DisplayName("CHR-02 IAM UTCID02 [N]: findProfilesByEmployeeIds with empty or null collection returns empty map")
    void findProfilesByEmployeeIds_emptyOrNullIds_returnsEmptyMap() {
        Map<Long, EmployeeProfileSnapshot> nullResult = service.findProfilesByEmployeeIds(null);
        assertThat(nullResult).isEmpty();

        Map<Long, EmployeeProfileSnapshot> emptyResult = service.findProfilesByEmployeeIds(Collections.emptyList());
        assertThat(emptyResult).isEmpty();

        verifyNoInteractions(employeeRepository);
    }

    @Test
    @DisplayName("CHR-02 IAM UTCID03 [N]: findProfileByEmployeeId returns optional employee snapshot")
    void findProfileByEmployeeId_validId_returnsSnapshot() {
        EmployeeProfileSnapshot emp = new EmployeeProfileSnapshot(4L, "Test Designer", "designer@lumintra.com");

        when(employeeRepository.findProfileSnapshotsByEmployeeIds(anyCollection()))
                .thenReturn(List.of(emp));

        Optional<EmployeeProfileSnapshot> result = service.findProfileByEmployeeId(4L);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(emp);
    }

    @Test
    @DisplayName("CHR-02 IAM UTCID04 [N]: findProfileByEmployeeId with null or non-positive ID returns empty optional")
    void findProfileByEmployeeId_nullOrNonPositive_returnsEmptyOptional() {
        assertThat(service.findProfileByEmployeeId(null)).isEmpty();
        assertThat(service.findProfileByEmployeeId(0L)).isEmpty();
        assertThat(service.findProfileByEmployeeId(-1L)).isEmpty();

        verifyNoInteractions(employeeRepository);
    }
}
