package com.lumintra.backend.media;

import com.lumintra.backend.device.domain.service.DeviceOwnershipService;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.media.domain.model.MediaPurpose;
import com.lumintra.backend.media.domain.service.CommissionStateLookupService;
import com.lumintra.backend.media.domain.service.MediaAccessControlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaAccessControlServiceTest {

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private DeviceOwnershipService deviceOwnershipService;

    @Mock
    private CommissionStateLookupService commissionStateLookupService;

    @InjectMocks
    private MediaAccessControlService mediaAccessControlService;

    @Test
    @DisplayName("MED-01 [N]: COMMISSION_REFERENCE allows CUSTOMER role")
    void verifyAccess_commissionReference_whenCustomer_shouldPass() {
        assertThatCode(() -> mediaAccessControlService.verifyAccess("10", Set.of("CUSTOMER"), MediaPurpose.COMMISSION_REFERENCE, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MED-01 [N]: COMMISSION_REFERENCE allows ADMIN role")
    void verifyAccess_commissionReference_whenAdmin_shouldPass() {
        assertThatCode(() -> mediaAccessControlService.verifyAccess("1", Set.of("ADMIN"), MediaPurpose.COMMISSION_REFERENCE, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MED-01 [A]: COMMISSION_REFERENCE rejects non-customer/non-management role")
    void verifyAccess_commissionReference_whenUnauthorized_shouldThrow() {
        assertThatThrownBy(() -> mediaAccessControlService.verifyAccess("10", Set.of("GUEST"), MediaPurpose.COMMISSION_REFERENCE, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only customers are authorized to upload commission reference images.");
    }

    @Test
    @DisplayName("MED-01 [A]: COMMISSION_CHAT rejects missing targetId")
    void verifyAccess_commissionChat_whenMissingTargetId_shouldThrow() {
        assertThatThrownBy(() -> mediaAccessControlService.verifyAccess("10", Set.of("CUSTOMER"), MediaPurpose.COMMISSION_CHAT, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("targetId (commissionId) is required");
    }

    @Test
    @DisplayName("MED-01 [N]: COMMISSION_CHAT allows active participant customer")
    void verifyAccess_commissionChat_whenActiveParticipant_shouldPass() {
        when(commissionStateLookupService.isChatActiveAndParticipant(101L, 10L, Set.of("CUSTOMER")))
                .thenReturn(true);

        assertThatCode(() -> mediaAccessControlService.verifyAccess("10", Set.of("CUSTOMER"), MediaPurpose.COMMISSION_CHAT, 101L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MED-01 [A]: COMMISSION_CHAT rejects inactive room or non-participant")
    void verifyAccess_commissionChat_whenInactive_shouldThrow() {
        when(commissionStateLookupService.isChatActiveAndParticipant(101L, 10L, Set.of("CUSTOMER")))
                .thenReturn(false);

        assertThatThrownBy(() -> mediaAccessControlService.verifyAccess("10", Set.of("CUSTOMER"), MediaPurpose.COMMISSION_CHAT, 101L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Commission chat is not active");
    }

    @Test
    @DisplayName("MED-01 [A]: COMMISSION_DRAFT rejects non-designer role")
    void verifyAccess_commissionDraft_whenNonDesigner_shouldThrow() {
        assertThatThrownBy(() -> mediaAccessControlService.verifyAccess("10", Set.of("CUSTOMER"), MediaPurpose.COMMISSION_DRAFT, 101L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only designers are authorized");
    }

    @Test
    @DisplayName("MED-01 [A]: COMMISSION_DRAFT rejects missing targetId")
    void verifyAccess_commissionDraft_whenMissingTargetId_shouldThrow() {
        assertThatThrownBy(() -> mediaAccessControlService.verifyAccess("10", Set.of("DESIGNER"), MediaPurpose.COMMISSION_DRAFT, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("targetId (commissionId) is required");
    }

    @Test
    @DisplayName("MED-01 [N]: COMMISSION_DRAFT allows assigned designer")
    void verifyAccess_commissionDraft_whenAssignedDesigner_shouldPass() {
        when(internalEmployeeAccessService.requireActiveEmployeeId("10", Set.of("DESIGNER")))
                .thenReturn(50L);
        when(commissionStateLookupService.isAssignedDesigner(101L, 50L))
                .thenReturn(true);

        assertThatCode(() -> mediaAccessControlService.verifyAccess("10", Set.of("DESIGNER"), MediaPurpose.COMMISSION_DRAFT, 101L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MED-01 [A]: COMMISSION_DRAFT rejects unassigned designer")
    void verifyAccess_commissionDraft_whenNotAssignedDesigner_shouldThrow() {
        when(internalEmployeeAccessService.requireActiveEmployeeId("10", Set.of("DESIGNER")))
                .thenReturn(50L);
        when(commissionStateLookupService.isAssignedDesigner(101L, 50L))
                .thenReturn(false);

        assertThatThrownBy(() -> mediaAccessControlService.verifyAccess("10", Set.of("DESIGNER"), MediaPurpose.COMMISSION_DRAFT, 101L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Designer is not assigned to this commission.");
    }

    @Test
    @DisplayName("MED-01 [A]: WARRANTY_EVIDENCE rejects non-customer role")
    void verifyAccess_warrantyEvidence_whenNonCustomer_shouldThrow() {
        assertThatThrownBy(() -> mediaAccessControlService.verifyAccess("10", Set.of("STAFF"), MediaPurpose.WARRANTY_EVIDENCE, 2001L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only customers are authorized to upload warranty evidence.");
    }

    @Test
    @DisplayName("MED-01 [A]: WARRANTY_EVIDENCE rejects missing targetId")
    void verifyAccess_warrantyEvidence_whenMissingTargetId_shouldThrow() {
        assertThatThrownBy(() -> mediaAccessControlService.verifyAccess("10", Set.of("CUSTOMER"), MediaPurpose.WARRANTY_EVIDENCE, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("targetId (deviceId) is required");
    }

    @Test
    @DisplayName("MED-01 [N]: WARRANTY_EVIDENCE allows device owner")
    void verifyAccess_warrantyEvidence_whenOwner_shouldPass() {
        when(deviceOwnershipService.isDeviceOwnedByCustomer(2001L, 10L)).thenReturn(true);

        assertThatCode(() -> mediaAccessControlService.verifyAccess("10", Set.of("CUSTOMER"), MediaPurpose.WARRANTY_EVIDENCE, 2001L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MED-01 [A]: WARRANTY_EVIDENCE rejects unowned device")
    void verifyAccess_warrantyEvidence_whenNotOwner_shouldThrow() {
        when(deviceOwnershipService.isDeviceOwnedByCustomer(2001L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> mediaAccessControlService.verifyAccess("10", Set.of("CUSTOMER"), MediaPurpose.WARRANTY_EVIDENCE, 2001L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Device does not belong to the authenticated customer.");
    }

    @Test
    @DisplayName("MED-01 [N]: CHARACTER_ASSET allows active designer in IAM")
    void verifyAccess_characterAsset_whenDesigner_shouldPass() {
        when(internalEmployeeAccessService.requireActiveEmployeeId("10", Set.of("DESIGNER"))).thenReturn(50L);

        assertThatCode(() -> mediaAccessControlService.verifyAccess("10", Set.of("DESIGNER"), MediaPurpose.CHARACTER_ASSET, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MED-01 [N]: CHARACTER_ASSET allows MANAGER in IAM")
    void verifyAccess_characterAsset_whenManager_shouldPass() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("99", Set.of("MANAGER", "ADMIN"))).thenReturn(99L);

        assertThatCode(() -> mediaAccessControlService.verifyAccess("99", Set.of("MANAGER"), MediaPurpose.CHARACTER_ASSET, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MED-01 [A]: CHARACTER_ASSET rejects CUSTOMER role")
    void verifyAccess_characterAsset_whenCustomer_shouldThrow() {
        assertThatThrownBy(() -> mediaAccessControlService.verifyAccess("10", Set.of("CUSTOMER"), MediaPurpose.CHARACTER_ASSET, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only designers, managers, and admins are authorized to upload character assets.");
    }

    @Test
    @DisplayName("MED-01 [N]: STORE_PRODUCT allows ADMIN role")
    void verifyAccess_storeProduct_whenAdmin_shouldPass() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("1", Set.of("MANAGER", "ADMIN"))).thenReturn(1L);

        assertThatCode(() -> mediaAccessControlService.verifyAccess("1", Set.of("ADMIN"), MediaPurpose.STORE_PRODUCT, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MED-01 [A]: STORE_PRODUCT rejects DESIGNER / CUSTOMER role")
    void verifyAccess_storeProduct_whenDesigner_shouldThrow() {
        assertThatThrownBy(() -> mediaAccessControlService.verifyAccess("10", Set.of("DESIGNER"), MediaPurpose.STORE_PRODUCT, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only managers and admins are authorized to upload store product media.");
    }

    @Test
    @DisplayName("MED-01 [N]: PRODUCT_LED, PRODUCT_BUNDLE, PRODUCT_SUBSCRIPTION allow MANAGER/ADMIN and reject CUSTOMER")
    void verifyAccess_typedProductPurposes_enforcesManagerOrAdmin() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("1", Set.of("MANAGER", "ADMIN"))).thenReturn(1L);

        for (MediaPurpose purpose : java.util.List.of(
                MediaPurpose.PRODUCT_LED,
                MediaPurpose.PRODUCT_BUNDLE,
                MediaPurpose.PRODUCT_SUBSCRIPTION)) {
            assertThatCode(() -> mediaAccessControlService.verifyAccess("1", Set.of("MANAGER"), purpose, null))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> mediaAccessControlService.verifyAccess("10", Set.of("CUSTOMER"), purpose, null))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only managers and admins are authorized to upload store product media.");
        }
    }
}
