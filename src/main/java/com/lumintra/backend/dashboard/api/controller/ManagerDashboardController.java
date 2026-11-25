package com.lumintra.backend.dashboard.api.controller;

import com.lumintra.backend.dashboard.api.request.ManagerDashboardFilterRequest;
import com.lumintra.backend.dashboard.api.request.ManagerRevenueDrilldownRequest;
import com.lumintra.backend.dashboard.api.response.ManagerKpiOverviewResponse;
import com.lumintra.backend.dashboard.api.response.ManagerOrderAuditDetailResponse;
import com.lumintra.backend.dashboard.api.response.ManagerRevenueDrilldownResponse;
import com.lumintra.backend.dashboard.application.GetManagerDashboardKpiUseCase;
import com.lumintra.backend.dashboard.application.GetManagerRevenueDrilldownUseCase;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Manager Dashboard",
        description = "Endpoints for management (MANAGER, ADMIN) to monitor business KPIs, revenue analytics, inventory health, and pending operational queues"
)
@RestController
@RequestMapping("/api/management/admin/dashboard")
@RequiredArgsConstructor
public class ManagerDashboardController {

    private final GetManagerDashboardKpiUseCase getManagerDashboardKpiUseCase;
    private final GetManagerRevenueDrilldownUseCase getManagerRevenueDrilldownUseCase;

    @Operation(
            summary = "Get manager operations KPI & analytics overview",
            description = "Retrieves an aggregated business KPI overview, including total revenue with like-for-like month-to-date (MTD) comparison, 5-category revenue breakdown (Hardware, Commission, Bundle, Character, Subscription), top 5 best-selling products, operational queue counts, and critically low LED stock models."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Manager operational overview retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: invalid month or comparison format",
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
                                              "timestamp": "2026-11-22T12:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": [
                                                {
                                                  "field": "month",
                                                  "message": "month must be in YYYY-MM format"
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
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: caller lacks MANAGER/ADMIN role or internal account is suspended",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @GetMapping("/kpi")
    public ResponseEntity<ApiResponse<ManagerKpiOverviewResponse>> getKpiOverview(
            @Valid @ModelAttribute ManagerDashboardFilterRequest request,
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh,
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated manager identity is required.");
        }

        String subject = authentication.getName();
        ManagerKpiOverviewResponse response = refresh
                ? getManagerDashboardKpiUseCase.execute(subject, request, true)
                : getManagerDashboardKpiUseCase.execute(subject, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Drill down into orders & commissions backing dashboard revenue metrics",
            description = "Returns the exact orders, line items, and commissions contributing to total revenue, a specific category, or a specific top-selling product within the reporting month."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @GetMapping("/revenue-sources")
    public ResponseEntity<ApiResponse<ManagerRevenueDrilldownResponse>> getRevenueSources(
            @Valid @ModelAttribute ManagerRevenueDrilldownRequest request,
            Authentication authentication) {
        String subject = requireAuthenticatedSubject(authentication);
        ManagerRevenueDrilldownResponse response = getManagerRevenueDrilldownUseCase.execute(subject, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Get full order audit detail for manager dashboard drill-down",
            description = "Retrieves full line items, shipping fee, subtotal, total amount, and payment transaction status for a specific order referenced in dashboard revenue analytics."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @GetMapping("/revenue-sources/orders/{orderId}")
    public ResponseEntity<ApiResponse<ManagerOrderAuditDetailResponse>> getOrderAuditDetail(
            @PathVariable Long orderId,
            Authentication authentication) {
        String subject = requireAuthenticatedSubject(authentication);
        ManagerOrderAuditDetailResponse response = getManagerRevenueDrilldownUseCase.getOrderAuditDetail(subject, orderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String requireAuthenticatedSubject(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated manager identity is required.");
        }
        return authentication.getName();
    }
}
