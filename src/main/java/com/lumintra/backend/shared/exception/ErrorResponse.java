package com.lumintra.backend.shared.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Schema(description = "Standard error response payload")
@Getter
@Builder
public class ErrorResponse {

    @Schema(description = "Stable machine-readable error code", example = "VALIDATION_FAILED")
    private String code;

    @Schema(description = "Safe human-readable error description", example = "Invalid request payload")
    private String message;

    @Schema(description = "HTTP status code matching the response", example = "400")
    private int status;

    @Schema(description = "ISO-8601 UTC timestamp of when the error occurred", example = "2026-10-29T07:30:00Z")
    private String timestamp;

    @Schema(description = "Unique trace ID for support and diagnostics", example = "trace-12345")
    private String traceId;

    @Schema(description = "List of structured problem details related to the error")
    private List<ErrorDetail> details;

    @Schema(description = "Field-level validation error detail")
    @Getter
    @Builder
    public static class ErrorDetail {

        @Schema(description = "The request field that caused the error", example = "email")
        private String field;

        @Schema(description = "Description of the validation failure on this field", example = "Invalid email format")
        private String message;
    }
}