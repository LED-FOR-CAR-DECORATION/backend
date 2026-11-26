package com.lumintra.backend.iam;

import com.lumintra.backend.iam.infrastructure.bootstrap.IamDataSeeder;
import com.lumintra.backend.iam.infrastructure.persistence.entity.CustomerEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.CustomerRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.RoleRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IamDataSeederTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private IamDataSeeder seeder;

    @Captor
    private ArgumentCaptor<EmployeeEntity> employeeCaptor;

    @Test
    void run_cleanDatabase_seedsSystemEmployeesWithoutMauSuffix() {
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@lumintra.com");
        ReflectionTestUtils.setField(seeder, "adminPassword", "Admin@123");
        ReflectionTestUtils.setField(seeder, "managerEmail", "manager@lumintra.com");
        ReflectionTestUtils.setField(seeder, "managerPassword", "Manager@123");
        ReflectionTestUtils.setField(seeder, "customerEmail", "customer@lumintra.com");
        ReflectionTestUtils.setField(seeder, "customerPassword", "Customer@123");
        ReflectionTestUtils.setField(seeder, "staffEmail", "staff@lumintra.com");
        ReflectionTestUtils.setField(seeder, "staffPassword", "Staff@123");
        ReflectionTestUtils.setField(seeder, "designerEmail", "designer@lumintra.com");
        ReflectionTestUtils.setField(seeder, "designerPassword", "Designer@123");

        Map<String, RoleEntity> rolesByName = new HashMap<>();
        given(roleRepository.findByRoleName(anyString())).willAnswer(inv -> Optional.ofNullable(rolesByName.get(inv.getArgument(0))));
        given(roleRepository.save(any(RoleEntity.class))).willAnswer(inv -> {
            RoleEntity role = inv.getArgument(0);
            rolesByName.put(role.getRoleName(), role);
            return role;
        });
        given(userAccountRepository.findByEmail(anyString())).willReturn(Optional.empty());
        given(passwordEncoder.encode(anyString())).willReturn("encoded");
        given(userAccountRepository.save(any(UserAccountEntity.class))).willAnswer(inv -> inv.getArgument(0));
        given(employeeRepository.save(any(EmployeeEntity.class))).willAnswer(inv -> inv.getArgument(0));
        given(customerRepository.save(any(CustomerEntity.class))).willAnswer(inv -> inv.getArgument(0));

        seeder.run(applicationArguments);

        verify(employeeRepository, atLeastOnce()).save(employeeCaptor.capture());
        assertThat(employeeCaptor.getAllValues())
                .extracting(e -> e.getRole().getRoleName(), EmployeeEntity::getFullName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ADMIN", "Quản trị viên hệ thống"),
                        org.assertj.core.groups.Tuple.tuple("MANAGER", "Quản lý"),
                        org.assertj.core.groups.Tuple.tuple("STAFF", "Nhân viên"),
                        org.assertj.core.groups.Tuple.tuple("DESIGNER", "Nhà thiết kế")
                );
    }
}
