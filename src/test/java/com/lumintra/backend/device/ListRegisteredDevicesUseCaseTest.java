package com.lumintra.backend.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumintra.backend.device.api.response.RegisteredDeviceResponse;
import com.lumintra.backend.device.application.ListRegisteredDevicesUseCase;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class ListRegisteredDevicesUseCaseTest {
    @Test
    void listsBoundDevicesInOrderAndExcludesUnboundAndOtherOwners() {
        var database =
                new EmbeddedDatabaseBuilder()
                        .generateUniqueName(true)
                        .setType(EmbeddedDatabaseType.H2)
                        .build();
        try {
            var jdbc = JdbcClient.create(database);
            jdbc.sql(
                            """
                            CREATE TABLE led_instance (
                                instance_id bigint PRIMARY KEY, model_id bigint, user_id bigint,
                                serial_number varchar(100), status varchar(30)
                            )
                            """)
                    .update();
            jdbc.sql(
                            """
                            INSERT INTO led_instance VALUES
                                (9, 3, 42, 'LED-9', 'ACTIVE'), (7, 3, 42, 'LED-7', 'ACTIVE'),
                                (8, 3, 41, 'LED-8', 'ACTIVE'), (6, 3, NULL, 'LED-6', 'ACTIVE')
                            """)
                    .update();

            var useCase = new ListRegisteredDevicesUseCase(jdbc);
            assertThat(useCase.execute(42L))
                    .containsExactly(
                            new RegisteredDeviceResponse(7L, 3L, "LED-7", "ACTIVE"),
                            new RegisteredDeviceResponse(9L, 3L, "LED-9", "ACTIVE"));
            assertThat(useCase.execute(43L)).isEmpty();
        } finally {
            database.shutdown();
        }
    }
}
