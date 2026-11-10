package com.lumintra.backend.shared.exception;

import com.lumintra.backend.shared.exception.commerce.*;
import com.lumintra.backend.shared.exception.iam.*;
import com.lumintra.backend.shared.exception.store.InvalidPriceRangeException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Tracer tracer;

    // Store exception
    @ExceptionHandler(InvalidPriceRangeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPriceRangeException(InvalidPriceRangeException ex) {
        return buildErrorResponse("INVALID_PRICE_RANGE", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    // Commerce exceptions
    @ExceptionHandler(ProductNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleProductNotAvailable(ProductNotAvailableException ex) {
        return buildErrorResponse("PRODUCT_NOT_AVAILABLE", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ProductAlreadyOwnedException.class)
    public ResponseEntity<ErrorResponse> handleProductAlreadyOwned(ProductAlreadyOwnedException ex) {
        return buildErrorResponse("PRODUCT_ALREADY_OWNED", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DigitalItemQuantityLimitException.class)
    public ResponseEntity<ErrorResponse> handleDigitalItemQuantityLimit(DigitalItemQuantityLimitException ex) {
        return buildErrorResponse("DIGITAL_ITEM_QUANTITY_LIMIT", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(InsufficientStockException ex) {
        return buildErrorResponse("INSUFFICIENT_STOCK", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidCartItemException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCartItem(InvalidCartItemException ex) {
        return buildErrorResponse("INVALID_CART_ITEM", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DeliveryAddressRequiredException.class)
    public ResponseEntity<ErrorResponse> handleDeliveryAddressRequired(DeliveryAddressRequiredException ex) {
        return buildErrorResponse("DELIVERY_ADDRESS_REQUIRED", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AddressNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAddressNotFound(AddressNotFoundException ex) {
        return buildErrorResponse("ADDRESS_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    // IAM exceptions
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return buildErrorResponse("INVALID_CREDENTIALS", ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException ex) {
        return buildErrorResponse("ACCOUNT_LOCKED", ex.getMessage(), HttpStatus.LOCKED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException ex) {
        return buildErrorResponse("RESOURCE_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        return buildErrorResponse("EMAIL_ALREADY_REGISTERED", ex.getMessage(), HttpStatus.CONFLICT);
    }

    // IAM specific exceptions from origin/develop
    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOtp(InvalidOtpException ex) {
        return buildErrorResponse("INVALID_OTP", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccountAlreadyVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleAccountAlreadyVerified(AccountAlreadyVerifiedException ex) {
        return buildErrorResponse("ACCOUNT_ALREADY_VERIFIED", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return buildErrorResponse("USER_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MaxOtpAttemptsException.class)
    public ResponseEntity<ErrorResponse> handleMaxOtpAttempts(MaxOtpAttemptsException ex) {
        return buildErrorResponse("MAX_OTP_ATTEMPTS_REACHED", ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(ResendCooldownException.class)
    public ResponseEntity<ErrorResponse> handleResendCooldown(ResendCooldownException ex) {
        return buildErrorResponse("RESEND_COOLDOWN_ACTIVE", ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(MaxResendAttemptsException.class)
    public ResponseEntity<ErrorResponse> handleMaxResendAttempts(MaxResendAttemptsException ex) {
        return buildErrorResponse("MAX_RESEND_ATTEMPTS_REACHED", ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS);
    }

    // General validation exception (covers both body and query param validation)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<ErrorResponse.ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> ErrorResponse.ErrorDetail.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .build())
                .toList();
        return buildErrorResponse("VALIDATION_FAILED", "Invalid request payload", HttpStatus.BAD_REQUEST, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return buildErrorResponse("VALIDATION_FAILED", "Malformed JSON request or invalid field value", HttpStatus.BAD_REQUEST);
    }

    // Security exceptions
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        return buildErrorResponse("UNAUTHORIZED", "Authentication is required.", HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        return buildErrorResponse("FORBIDDEN", "Access is denied.", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unexpected system failure occurred", exception);
        return buildErrorResponse("INTERNAL_SERVER_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Helpers
    private ResponseEntity<ErrorResponse> buildErrorResponse(String code, String message, HttpStatus status) {
        return buildErrorResponse(code, message, status, List.of());
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(String code, String message, HttpStatus status, List<ErrorResponse.ErrorDetail> details) {
        String currentTraceId = Optional.ofNullable(tracer.currentSpan())
                .map(Span::context)
                .map(TraceContext::traceId)
                .orElse("trace-id-unavailable");

        ErrorResponse response = ErrorResponse.builder()
                .code(code)
                .message(message)
                .status(status.value())
                .timestamp(Instant.now().toString())
                .traceId(currentTraceId)
                .details(details)
                .build();

        return ResponseEntity.status(status).body(response);
    }
}
