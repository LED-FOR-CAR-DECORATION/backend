package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.response.RegisteredDeviceResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListRegisteredDevicesUseCase {
    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public List<RegisteredDeviceResponse> execute(long userId) {
        return jdbc.sql(
                        """
                        SELECT instance_id, model_id, serial_number, status
                        FROM led_instance
                        WHERE user_id = :userId
                        ORDER BY instance_id
                        """)
                .param("userId", userId)
                .query(
                        (row, index) ->
                                new RegisteredDeviceResponse(
                                        row.getLong("instance_id"),
                                                row.getObject("model_id", Long.class),
                                        row.getString("serial_number"), row.getString("status")))
                .list();
    }
}
