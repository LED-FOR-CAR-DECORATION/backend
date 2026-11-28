package com.lumintra.backend.monitoring.api.controller;

import com.lumintra.backend.monitoring.api.request.LiveSystemMetricsRequest;
import com.lumintra.backend.monitoring.api.request.MetricsHistoryRequest;
import com.lumintra.backend.monitoring.api.response.LiveSystemMetricsResponse;
import com.lumintra.backend.monitoring.api.response.MetricsHistoryResponse;
import com.lumintra.backend.monitoring.application.GetLiveSystemMetricsUseCase;
import com.lumintra.backend.monitoring.application.GetMetricsHistoryUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "System Monitoring",
        description = "Endpoints for ADMIN to monitor API performance and the health of the backend's components (Feature 17)"
)
@RestController
@RequestMapping("/api/management/admin/system/metrics")
@RequiredArgsConstructor
public class SystemMetricsController {

    private final GetLiveSystemMetricsUseCase getLiveSystemMetricsUseCase;
    private final GetMetricsHistoryUseCase getMetricsHistoryUseCase;

    @Operation(
            summary = "Get live system metrics",
            description = """
                    Returns API traffic, latency (avg, p50/p95/p99, max), error counts and the busiest or slowest endpoints \
                    over the last 5, 15 or 60 completed minutes, plus current database, Redis and JVM health. \
                    Figures come from the in-memory per-minute snapshots of the instance that serves the request and \
                    reset when it restarts. Percentiles are estimated from fixed latency buckets (BR-GEN-26). \
                    Intended to be polled every 10-15 seconds."""
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Live metrics retrieved successfully. Components that are unreachable are reported as DOWN."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: invalid window, sort or limit",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Validation Failed",
                                    value = """
                                            {
                                              "code": "VALIDATION_FAILED",
                                              "message": "Invalid request parameter",
                                              "status": 400,
                                              "timestamp": "2026-11-26T15:45:00Z",
                                              "traceId": "trace-12345",
                                              "details": [
                                                {
                                                  "field": "window",
                                                  "message": "window must be one of 5m, 15m, 60m"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized: missing, expired, or invalid JWT authentication token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: caller is not an ADMIN (BR-GEN-22)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<LiveSystemMetricsResponse>> getLiveMetrics(
            @Valid @ModelAttribute LiveSystemMetricsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(getLiveSystemMetricsUseCase.execute(request)));
    }

    @Operation(
            summary = "Get metrics history",
            description = """
                    Returns API performance over a range of up to 30 days, grouped into 1m, 5m, 15m, 1h or 1d periods \
                    (1d follows calendar days in Asia/Ho_Chi_Minh), for the whole system or one method/route. \
                    Figures add up the stored per-minute summaries of every backend instance. Periods without \
                    traffic are included with zero counts. The current, unfinished minute is never included. \
                    Percentiles are estimated from fixed latency buckets (BR-GEN-26)."""
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Metrics history retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: missing or malformed parameters (VALIDATION_FAILED), or a range that is "
                            + "reversed, older than 30 days, in the future or would produce more than 1,500 points (INVALID_METRICS_RANGE)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Too many points",
                                    value = """
                                            {
                                              "code": "INVALID_METRICS_RANGE",
                                              "message": "The range would produce more than 1500 points; use a larger interval or a shorter range",
                                              "status": 400,
                                              "timestamp": "2026-11-28T03:00:00Z",
                                              "traceId": "trace-12345"
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized: missing, expired, or invalid JWT authentication token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: caller is not an ADMIN (BR-GEN-22)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<MetricsHistoryResponse>> getMetricsHistory(
            @Valid @ModelAttribute MetricsHistoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(getMetricsHistoryUseCase.execute(request)));
    }
}
