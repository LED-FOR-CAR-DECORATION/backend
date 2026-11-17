package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.response.FirmwareReleaseSummary;
import com.lumintra.backend.device.domain.model.FirmwareReleaseStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListFirmwareReleasesUseCase {
    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public List<FirmwareReleaseSummary> execute(Long modelId, FirmwareReleaseStatus status) {
        return jdbc.sql(
                        """
SELECT firmware_id, model_id, version_name, status, release_notes, released_at
FROM firmware_version
WHERE (:modelId IS NULL OR model_id = :modelId)
    AND (:status IS NULL OR status = :status)
ORDER BY released_at DESC NULLS LAST, firmware_id DESC
""")
                .param("modelId", modelId, Types.BIGINT)
                .param("status", status == null ? null : status.name(), Types.VARCHAR)
                .query(
                        (row, index) ->
                                new FirmwareReleaseSummary(
                                        row.getLong("firmware_id"),
                                        row.getLong("model_id"),
                                        row.getString("version_name"),
                                        row.getString("status"),
                                        row.getString("release_notes"),
                                        row.getObject("released_at", OffsetDateTime.class)))
                .list();
    }
}
