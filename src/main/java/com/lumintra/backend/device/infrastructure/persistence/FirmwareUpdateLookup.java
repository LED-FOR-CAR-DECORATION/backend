package com.lumintra.backend.device.infrastructure.persistence;

import com.lumintra.backend.shared.exception.iam.UserNotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
@RequiredArgsConstructor
public class FirmwareUpdateLookup {
    private final NamedParameterJdbcTemplate jdbc;

    public String activeVersion(String deviceId, long ownerId) {
        var devices =
                jdbc.query(
                        """
SELECT release.version
FROM firmware_device device
JOIN user_account owner ON owner.account_id = device.owner_account_id
LEFT JOIN firmware_channel channel ON channel.hardware_revision = device.hardware_revision
LEFT JOIN firmware_release release ON release.id = channel.release_id
    AND release.hardware_revision = device.hardware_revision
    AND release.deprecated = FALSE
WHERE device.id = :deviceId AND device.owner_account_id = :ownerId
    AND owner.account_type = 'CUSTOMER' AND owner.status = 'ACTIVE'
""",
                        Map.of("deviceId", deviceId, "ownerId", ownerId),
                        (row, index) -> row.getString("version"));
        if (devices.isEmpty()) {
            throw new UserNotFoundException("Device not found.");
        }
        return devices.getFirst();
    }
}
