package com.lumintra.backend.monitoring.api.controller;

import com.lumintra.backend.monitoring.api.request.LiveSystemMetricsRequest;
import com.lumintra.backend.monitoring.api.response.LiveSystemMetricsResponse;
import com.lumintra.backend.monitoring.application.GetLiveSystemMetricsUseCase;
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
}
