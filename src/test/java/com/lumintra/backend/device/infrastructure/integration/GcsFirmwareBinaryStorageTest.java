package com.lumintra.backend.device.infrastructure.integration;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.lumintra.backend.device.domain.service.FirmwareBinaryStorage;
import com.lumintra.backend.shared.exception.media.StorageServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GcsFirmwareBinaryStorageTest {

    private final Storage storage = mock(Storage.class);
    private final GcsFirmwareBinaryStorage binaryStorage =
            new GcsFirmwareBinaryStorage(storage, "private-bucket", "default-bucket");

    @Test
    @DisplayName("DEV-23 Storage UTCID01 [N]: Opens the private-bucket object with its size and a chunked reader")
    void open_existingObject_returnsSizeAndStream() {
        Blob blob = mock(Blob.class);
        ReadChannel reader = mock(ReadChannel.class);
        when(storage.get(BlobId.of("private-bucket", "firmware/3/1.2.0.bin"))).thenReturn(blob);
        when(blob.exists()).thenReturn(true);
        when(blob.getSize()).thenReturn(1024L);
        when(blob.reader()).thenReturn(reader);

        FirmwareBinaryStorage.StoredBinary binary = binaryStorage.open("firmware/3/1.2.0.bin");

        assertThat(binary.size()).isEqualTo(1024L);
        assertThat(binary.content()).isNotNull();
        verify(blob).reader();
    }

    @Test
    @DisplayName("DEV-23 Storage UTCID02 [A]: A missing object fails before any stream is opened")
    void open_missingObject_throws() {
        when(storage.get(BlobId.of("private-bucket", "firmware/3/9.9.9.bin"))).thenReturn(null);

        assertThatThrownBy(() -> binaryStorage.open("firmware/3/9.9.9.bin")).isInstanceOf(StorageServiceException.class);
    }

    @Test
    @DisplayName("DEV-23 Storage UTCID03 [A]: A storage lookup failure is wrapped as a storage error")
    void open_lookupFailure_wrapped() {
        Blob blob = mock(Blob.class);
        when(storage.get(BlobId.of("private-bucket", "k"))).thenThrow(new RuntimeException("network"));

        assertThatThrownBy(() -> binaryStorage.open("k")).isInstanceOf(StorageServiceException.class);
        verify(blob, never()).reader();
    }
}
