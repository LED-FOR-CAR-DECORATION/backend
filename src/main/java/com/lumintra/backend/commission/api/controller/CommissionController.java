package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.request.CustomerCommissionFilterRequest;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest;
import com.lumintra.backend.commission.api.response.ApproveDeliverableCardResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse.CustomerCommissionSummaryItem;
import com.lumintra.backend.commission.application.ApproveDeliverableCardUseCase;
import com.lumintra.backend.commission.application.GetCustomerCommissionDetailUseCase;
import com.lumintra.backend.commission.application.ListCustomerCommissionsUseCase;
import com.lumintra.backend.commission.application.SubmitCommissionUseCase;
import com.lumintra.backend.commission.api.request.SendCommissionMessageRequest;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.application.ListCommissionMessagesUseCase;
import com.lumintra.backend.commission.application.SendCommissionMessageUseCase;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@Tag(
        name = "Customer Commissions",
        description = "Customer endpoints for submitting and tracking personal custom artwork commission requests"
)
@RestController
@RequestMapping("/api/commissions")
@RequiredArgsConstructor
public class CommissionController {

    private static final Set<String> MANAGEMENT_CHAT_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    private final ListCustomerCommissionsUseCase listCustomerCommissionsUseCase;
    private final GetCustomerCommissionDetailUseCase getCustomerCommissionDetailUseCase;
    private final SubmitCommissionUseCase submitCommissionUseCase;
    private final ListCommissionMessagesUseCase listCommissionMessagesUseCase;
    private final SendCommissionMessageUseCase sendCommissionMessageUseCase;
    private final ApproveDeliverableCardUseCase approveDeliverableCardUseCase;
    private final InternalEmployeeAccessService internalEmployeeAccessService;

    @Operation(
            summary = "List personal commission requests",
            description = "Retrieves a paginated list of custom artwork commission requests owned by the authenticated CUSTOMER account, supporting optional status filtering and sorting by submission date (`createdAt` ASC or DESC).",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Paginated list of personal commission requests retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination, status enum, or sortDirection query parameter",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ValidationFailedExample",
                                    value = """
                                            {
                                              "code": "VALIDATION_FAILED",
                                              "message": "Invalid request parameter",
                                              "status": 400,
                                              "timestamp": "2026-11-16T02:00:00Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": [
                                                {
                                                  "field": "page",
                                                  "message": "page must be greater than or equal to 0"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, expired, or invalid JWT access token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "UnauthorizedExample",
                                    value = """
                                            {
                                              "code": "UNAUTHORIZED",
                                              "message": "Authentication is required.",
                                              "status": 401,
                                              "timestamp": "2026-11-16T02:00:00Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Authenticated caller does not hold the CUSTOMER role",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ForbiddenRoleExample",
                                    value = """
                                            {
                                              "code": "FORBIDDEN",
                                              "message": "Access is denied.",
                                              "status": 403,
                                              "timestamp": "2026-11-16T02:00:00Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InternalServerErrorExample",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "An unexpected error occurred",
                                              "status": 500,
                                              "timestamp": "2026-11-16T02:00:00Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": []
                                            }
                                            """
                            )
                    )
            )
    })
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<CustomerCommissionSummaryItem>>> listMyCommissions(
            Authentication authentication,
            @Valid @ModelAttribute CustomerCommissionFilterRequest filter) {
        Long customerId = extractCustomerId(authentication);
        CustomerCommissionListResponse result = listCustomerCommissionsUseCase.execute(customerId, filter);
        return ResponseEntity.ok(ApiResponse.success(result.items(), result.meta()));
    }

    @Operation(
            summary = "Submit custom commission request",
            description = "Submits a new custom artwork commission request using a preset (BASIC, ADVANCED) or CUSTOM configurator, validating rule constraints, snapshotting pricing breakdown, auto-assigning staff, and saving reference attachments.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Commission request submitted successfully and queued as PENDING_REVIEW",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CustomerCommissionDetailResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation failed, invalid rule selection, or preset mismatch",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, expired, or invalid JWT access token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied: caller does not possess the CUSTOMER role",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Specified pricing tier or rule config/option not found or inactive",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server failure",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerCommissionDetailResponse>> submitCommission(
            Authentication authentication,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SubmitCommissionRequest.class),
                            examples = @ExampleObject(
                                    name = "CustomCommissionExample",
                                    value = """
                                        {
                                          "title": "Cyberpunk Shiba Inu Pixel Art",
                                          "description": "Pixel art shiba with blinking neon effects...",
                                          "tierCode": "CUSTOM",
                                          "selectedOptions": [
                                            { "configId": 1, "optionIds": [2] },
                                            { "configId": 2, "optionIds": [4, 8] },
                                            { "configId": 3, "quantity": 4 },
                                            { "configId": 4, "textValue": "Make the shiba have a glowing neon cyan outline, using the colors I have attached as image in the request" }
                                          ],
                                          "attachments": [
                                            {
                                              "imageUrl": "https://cdn.lumintra.com/commissions/references/42/ref-01.png",
                                              "caption": "Neon color palette reference"
                                            }
                                          ]
                                        }
                                        """
                            )
                    )
            )
            @Valid @RequestBody SubmitCommissionRequest request) {
        Long customerId = extractCustomerId(authentication);
        CustomerCommissionDetailResponse result = submitCommissionUseCase.execute(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @Operation(
            summary = "View personal commission request details",
            description = "Retrieves the complete customer-facing detail view of a commission request owned by the authenticated CUSTOMER account, including requirements, snapshotted pricing breakdown, reference attachments, `activePaymentCode` when payment is pending, and Staff-approved delivered previews (excluding unapproved internal Designer drafts).",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Commission request details retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid or non-positive commission id path parameter",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InvalidCommissionIdExample",
                                    value = """
                                            {
                                              "code": "VALIDATION_FAILED",
                                              "message": "Invalid request parameter",
                                              "status": 400,
                                              "timestamp": "2026-11-16T02:30:00Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": [
                                                {
                                                  "field": "id",
                                                  "message": "id must be greater than 0"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, expired, or invalid JWT access token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "UnauthorizedExample",
                                    value = """
                                            {
                                              "code": "UNAUTHORIZED",
                                              "message": "Authentication is required.",
                                              "status": 401,
                                              "timestamp": "2026-11-16T02:30:00Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold the CUSTOMER role or does not own the requested commission",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "OwnershipMismatchExample",
                                            value = """
                                                    {
                                                      "code": "COMMISSION_OWNERSHIP_MISMATCH",
                                                      "message": "You do not have permission to access this commission request.",
                                                      "status": 403,
                                                      "timestamp": "2026-11-16T02:30:00Z",
                                                      "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                                      "details": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "ForbiddenRoleExample",
                                            value = """
                                                    {
                                                      "code": "FORBIDDEN",
                                                      "message": "Access is denied.",
                                                      "status": 403,
                                                      "timestamp": "2026-11-16T02:30:00Z",
                                                      "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                                      "details": []
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Commission request does not exist",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "CommissionNotFoundExample",
                                    value = """
                                            {
                                              "code": "COMMISSION_NOT_FOUND",
                                              "message": "Commission request not found with id: 9999",
                                              "status": 404,
                                              "timestamp": "2026-11-16T02:30:00Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InternalServerErrorExample",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "An unexpected error occurred",
                                              "status": 500,
                                              "timestamp": "2026-11-16T02:30:00Z",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "details": []
                                            }
                                            """
                            )
                    )
            )
    })
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerCommissionDetailResponse>> getMyCommissionDetail(
            Authentication authentication,
            @Parameter(description = "Unique commission request identifier", example = "501")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id) {
        Long customerId = extractCustomerId(authentication);
        CustomerCommissionDetailResponse result = getCustomerCommissionDetailUseCase.execute(customerId, id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(
            summary = "List commission chat messages",
            description = """
                    Retrieves chronological messages (`TEXT`, `IMAGE`, `DELIVERABLE_CARD`, `EXTRA_FEE_CARD`, `SYSTEM`) in the commission chat room once status is at least `PAID`. Restricted to the owning `CUSTOMER` or `STAFF`/`MANAGER`/`ADMIN`.
                    
                    **WebSocket Pairing:** Connect to `ws://<host>/ws` with STOMP header `Authorization: Bearer <token>`
                    and subscribe to `/topic/commissions/{id}`. Note that WebSocket frames deliver `CommissionMessageResponse`
                    directly without the `{ "data": ... }` REST envelope.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'MANAGER', 'ADMIN')")
    @GetMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<List<CommissionMessageResponse>>> listCommissionMessages(
            Authentication authentication,
            @Parameter(description = "Unique commission request identifier", example = "105")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id) {

        boolean isCustomer = hasAuthority(authentication, "ROLE_CUSTOMER");
        CommissionMessageSenderRole callerRole = isCustomer
                ? CommissionMessageSenderRole.CUSTOMER
                : CommissionMessageSenderRole.STAFF;
        Long callerId = isCustomer
                ? extractCustomerId(authentication)
                : requireEmployeeId(authentication);

        List<CommissionMessageResponse> messages = listCommissionMessagesUseCase.execute(id, callerId, callerRole);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @Operation(
            summary = "Send commission chat message",
            description = """
                    Persists a `TEXT` or `IMAGE` message in an active commission chat room (`PAID` through `AWAITING_ESTIMATE`)
                    and broadcasts the saved `CommissionMessageResponse` payload to STOMP subscribers on `/topic/commissions/{id}`.

                    **WebSocket Pairing:** Connect to `ws://<host>/ws` with STOMP header `Authorization: Bearer <token>`
                    and subscribe to `/topic/commissions/{id}`. Note that WebSocket frames deliver `CommissionMessageResponse`
                    directly without the `{ "data": ... }` REST envelope.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'MANAGER', 'ADMIN')")
    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<CommissionMessageResponse>> sendCommissionMessage(
            Authentication authentication,
            @Parameter(description = "Unique commission request identifier", example = "105")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id,
            @Valid @RequestBody SendCommissionMessageRequest request) {

        boolean isCustomer = hasAuthority(authentication, "ROLE_CUSTOMER");
        CommissionMessageSenderRole senderRole = isCustomer
                ? CommissionMessageSenderRole.CUSTOMER
                : CommissionMessageSenderRole.STAFF;
        Long callerId = isCustomer
                ? extractCustomerId(authentication)
                : requireEmployeeId(authentication);
        boolean isPrivilegedManagement = hasAuthority(authentication, "ROLE_MANAGER")
                || hasAuthority(authentication, "ROLE_ADMIN");

        CommissionMessageResponse response = sendCommissionMessageUseCase.execute(
                id, callerId, senderRole, isPrivilegedManagement, request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(
            summary = "Approve Deliverable Card and complete commission (CMS-18)",
            description = """
                    Allows the owning `CUSTOMER` to approve the Deliverable Card currently delivered in the commission chat.
                    Marks the delivered submission `CUSTOMER_ACCEPTED` (card accepted), records it as `approvedSubmissionId`,
                    clears the auto-complete countdown, transitions the commission from `PREVIEW_DELIVERED` to `COMPLETED`,
                    posts a `SYSTEM` chat message and publishes `CommissionCompletedEvent`.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Deliverable Card approved; commission transitioned to COMPLETED",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApproveDeliverableCardResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid or non-positive commission id path parameter",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, expired, or invalid JWT access token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold the CUSTOMER role or does not own the commission (COMMISSION_OWNERSHIP_MISMATCH)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Commission request not found (COMMISSION_NOT_FOUND)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Commission is not PREVIEW_DELIVERED (e.g. already COMPLETED) or has no delivered preview awaiting approval (INVALID_COMMISSION_STATUS_TRANSITION)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/{id}/deliverable-card/approve")
    public ResponseEntity<ApiResponse<ApproveDeliverableCardResponse>> approveDeliverableCard(
            Authentication authentication,
            @Parameter(description = "Unique commission request identifier", example = "105")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id) {
        Long customerId = extractCustomerId(authentication);
        ApproveDeliverableCardResponse response = approveDeliverableCardUseCase.execute(id, customerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Long requireEmployeeId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Unauthorized");
        }
        return internalEmployeeAccessService.requireActiveEmployeeId(
                authentication.getName(), MANAGEMENT_CHAT_ROLES
        );
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private Long extractCustomerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Unauthorized");
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            throw new InvalidCredentialsException("Invalid authentication principal");
        }
        try {
            long id = Long.parseLong(name.trim());
            if (id <= 0) {
                throw new InvalidCredentialsException("Invalid customer ID in token");
            }
            return id;
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid customer ID in token");
        }
    }
}
