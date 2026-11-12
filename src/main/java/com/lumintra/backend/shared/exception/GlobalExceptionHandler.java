package com.lumintra.backend.shared.exception;

import com.lumintra.backend.shared.exception.commerce.*;
import com.lumintra.backend.shared.exception.iam.*;
import com.lumintra.backend.shared.exception.payment.PaymentSessionNotFoundException;
import com.lumintra.backend.shared.exception.store.InvalidPriceRangeException;
import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.shared.exception.store.StoreProductTagNotFoundException;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException;
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
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @ExceptionHandler(StoreProductReferenceException.class)
    public ResponseEntity<ErrorResponse> handleStoreProductReference(StoreProductReferenceException ex) {
        var status = ex.getReason() == StoreProductReferenceException.Reason.CHARACTER_VERSION_ALREADY_LINKED
                ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND;
        return buildErrorResponse(ex.getReason().name(), ex.getMessage(), status);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFoundException(ProductNotFoundException ex) {
        return buildErrorResponse("PRODUCT_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(StoreProductTagNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStoreProductTagNotFound(StoreProductTagNotFoundException ex) {
        return buildErrorResponse("TAG_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
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

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCartItemNotFound(CartItemNotFoundException ex) {
        return buildErrorResponse("CART_ITEM_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DeliveryAddressRequiredException.class)
    public ResponseEntity<ErrorResponse> handleDeliveryAddressRequired(DeliveryAddressRequiredException ex) {
        return buildErrorResponse("DELIVERY_ADDRESS_REQUIRED", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AddressNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAddressNotFound(AddressNotFoundException ex) {
        return buildErrorResponse("ADDRESS_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
        return buildErrorResponse("ORDER_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(OrderStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleOrderStatusTransition(OrderStatusTransitionException ex) {
        return buildErrorResponse("INVALID_ORDER_STATUS_TRANSITION", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CarrierDispatchException.class)
    public ResponseEntity<ErrorResponse> handleCarrierDispatchException(CarrierDispatchException ex) {
        return buildErrorResponse(ex.getErrorCode(), ex.getMessage(), ex.getHttpStatus());
    }

    // Payment exceptions
    @ExceptionHandler(PaymentSessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentSessionNotFound(PaymentSessionNotFoundException ex) {
        return buildErrorResponse("PAYMENT_SESSION_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
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
        String failedFields = details.stream()
                .map(detail -> detail.getField() + ":" + detail.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("none");
        log.debug("Request validation failed [fields={}]", failedFields);
        return buildErrorResponse("VALIDATION_FAILED", "Invalid request payload", HttpStatus.BAD_REQUEST, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String causeType = cause != null ? cause.getClass().getSimpleName() : ex.getClass().getSimpleName();
        log.debug("Unreadable HTTP request body [causeType={}]", causeType);
        return buildErrorResponse("VALIDATION_FAILED", "Malformed JSON request or invalid field value", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ErrorResponse.ErrorDetail detail = ErrorResponse.ErrorDetail.builder()
                .field(ex.getName())
                .message("Invalid parameter value")
                .build();
        return buildErrorResponse("VALIDATION_FAILED", "Invalid request parameter", HttpStatus.BAD_REQUEST, List.of(detail));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        List<ErrorResponse.ErrorDetail> details = ex.getParameterValidationResults().stream()
                .flatMap(paramResult -> {
                    String field = Optional.ofNullable(paramResult.getMethodParameter().getParameterName())
                            .orElse("parameter");
                    return paramResult.getResolvableErrors().stream()
                            .map(error -> ErrorResponse.ErrorDetail.builder()
                                    .field(error instanceof FieldError fieldError ? fieldError.getField() : field)
                                    .message(Optional.ofNullable(error.getDefaultMessage()).orElse("Invalid parameter value"))
                                    .build());
                })
                .toList();
        return buildErrorResponse("VALIDATION_FAILED", "Invalid request parameter", HttpStatus.BAD_REQUEST, details);
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

    @ExceptionHandler(PasswordResetLockedException.class)
    public ResponseEntity<ErrorResponse> handlePasswordResetLocked(PasswordResetLockedException ex) {
        return buildErrorResponse("PASSWORD_RESET_LOCKED", ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(PasswordResetUnavailableException.class)
    public ResponseEntity<ErrorResponse> handlePasswordResetUnavailable(PasswordResetUnavailableException ex) {
        return buildErrorResponse("PASSWORD_RESET_UNAVAILABLE", ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
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
