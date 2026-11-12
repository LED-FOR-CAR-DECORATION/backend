package com.lumintra.backend.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lumintra.backend.device.application.CheckFirmwareUpdateUseCase;
import com.lumintra.backend.device.infrastructure.persistence.FirmwareUpdateLookup;

import org.junit.jupiter.api.Test;

class CheckFirmwareUpdateUseCaseTest {
    private final FirmwareUpdateLookup lookup = mock(FirmwareUpdateLookup.class);
    private final CheckFirmwareUpdateUseCase useCase = new CheckFirmwareUpdateUseCase(lookup);

    @Test
    void onlyOffersNumericallyNewerVersions() {
        when(lookup.activeVersion("test-unit", 42)).thenReturn("1.10.0");

        assertThat(useCase.execute("42", "test-unit", "1.9.0").updateAvailable()).isTrue();
        assertThat(useCase.execute("42", "test-unit", "1.10").updateAvailable()).isFalse();
        assertThat(useCase.execute("42", "test-unit", "2.0.0").updateAvailable()).isFalse();
    }

    @Test
    void returnsNoUpdateWhenTheOwnedDeviceHasNoActiveRelease() {
        var result = useCase.execute("42", "test-unit", "1.0.0");

        assertThat(result.updateAvailable()).isFalse();
        assertThat(result.latestVersion()).isNull();
    }
}
