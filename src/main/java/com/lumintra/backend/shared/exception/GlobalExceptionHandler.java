package com.lumintra.backend.shared.exception;

import com.lumintra.backend.shared.exception.character.CannotDeleteListedCharacterException;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
import com.lumintra.backend.shared.exception.character.CharacterVersionAlreadyLinkedToProductException;
import com.lumintra.backend.shared.exception.character.CharacterVersionNotFoundException;
import com.lumintra.backend.shared.exception.commerce.*;
import com.lumintra.backend.shared.exception.commission.*;
import com.lumintra.backend.shared.exception.device.*;
import com.lumintra.backend.shared.exception.iam.*;
import com.lumintra.backend.shared.exception.media.InvalidMediaRequestException;
import com.lumintra.backend.shared.exception.media.InvalidMediaUrlException;
import com.lumintra.backend.shared.exception.media.StorageServiceException;
import com.lumintra.backend.shared.exception.payment.PaymentSessionNotFoundException;
import com.lumintra.backend.shared.exception.payment.PaymentSessionNotCancellableException;
import com.lumintra.backend.shared.exception.store.InvalidPriceRangeException;
import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.shared.exception.store.StoreProductTagNotFoundException;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException;
import com.lumintra.backend.shared.exception.store.InvalidBundlePriceException;
import com.lumintra.backend.shared.exception.store.TagAlreadyExistsException;
import com.lumintra.backend.shared.exception.store.SubscriptionNotFoundException;
import com.lumintra.backend.shared.exception.store.InvalidSubscriptionDurationException;
import com.lumintra.backend.shared.exception.store.PlanCodeAlreadyExistsException;
import com.lumintra.backend.shared.exception.store.BundleNotFoundException;
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
import jakarta.validation.ConstraintViolationException;
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
        var status = (ex.getReason() == StoreProductReferenceException.Reason.CHARACTER_VERSION_ALREADY_LINKED
                        || ex.getReason() == StoreProductReferenceException.Reason.LED_MODEL_ALREADY_LINKED)
                ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND;
        return buildErrorResponse(ex.getReason().name(), ex.getMessage(), status);
    }

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSubscriptionNotFound(SubscriptionNotFoundException ex) {
        return buildErrorResponse("SUBSCRIPTION_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidSubscriptionDurationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSubscriptionDuration(InvalidSubscriptionDurationException ex) {
        return buildErrorResponse("INVALID_SUBSCRIPTION_DURATION", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PlanCodeAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handlePlanCodeAlreadyExists(PlanCodeAlreadyExistsException ex) {
        return buildErrorResponse("PLAN_CODE_ALREADY_EXISTS", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(BundleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBundleNotFound(BundleNotFoundException ex) {
        return buildErrorResponse("BUNDLE_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidBundlePriceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBundlePrice(InvalidBundlePriceException ex) {
        return buildErrorResponse("INVALID_BUNDLE_PRICE", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CharacterNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCharacterNotFoundException(CharacterNotFoundException ex) {
        return buildErrorResponse("CHARACTER_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CannotDeleteListedCharacterException.class)
    public ResponseEntity<ErrorResponse> handleCannotDeleteListedCharacter(CannotDeleteListedCharacterException ex) {
        return buildErrorResponse("CANNOT_DELETE_LISTED_CHARACTER", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CharacterVersionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCharacterVersionNotFound(CharacterVersionNotFoundException ex) {
        return buildErrorResponse("VERSION_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CharacterVersionAlreadyLinkedToProductException.class)
    public ResponseEntity<ErrorResponse> handleCharacterVersionAlreadyLinkedToProduct(CharacterVersionAlreadyLinkedToProductException ex) {
        return buildErrorResponse("VERSION_ALREADY_LINKED_TO_PRODUCT", ex.getMessage(), HttpStatus.CONFLICT);
    }

    // Media exceptions
    @ExceptionHandler(InvalidMediaUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMediaUrlException(InvalidMediaUrlException ex) {
        return buildErrorResponse("VALIDATION_FAILED", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidMediaRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMediaRequestException(InvalidMediaRequestException ex) {
        return buildErrorResponse("VALIDATION_FAILED", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(StorageServiceException.class)
    public ResponseEntity<ErrorResponse> handleStorageServiceException(StorageServiceException ex) {
        log.error("Storage service failure [message={}]", ex.getMessage(), ex);
        return buildErrorResponse("STORAGE_SERVICE_ERROR", "Failed to interact with cloud storage service.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFoundException(ProductNotFoundException ex) {
        return buildErrorResponse("PRODUCT_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(StoreProductTagNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStoreProductTagNotFound(StoreProductTagNotFoundException ex) {
        return buildErrorResponse("TAG_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TagAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleTagAlreadyExists(TagAlreadyExistsException ex) {
        return buildErrorResponse("TAG_ALREADY_EXISTS", ex.getMessage(), HttpStatus.CONFLICT);
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

    @ExceptionHandler(LedInstanceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleLedInstanceNotFound(LedInstanceNotFoundException ex) {
        return buildErrorResponse("LED_INSTANCE_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(LedModelNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleLedModelNotFound(LedModelNotFoundException ex) {
        return buildErrorResponse("LED_MODEL_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidFirmwareBinaryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFirmwareBinary(InvalidFirmwareBinaryException ex) {
        return buildErrorResponse("INVALID_FIRMWARE_BINARY", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(FirmwareVersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleFirmwareVersionConflict(FirmwareVersionConflictException ex) {
        return buildErrorResponse("FIRMWARE_VERSION_CONFLICT", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(FirmwareNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFirmwareNotFound(FirmwareNotFoundException ex) {
        return buildErrorResponse("FIRMWARE_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(FirmwareNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleFirmwareNotActive(FirmwareNotActiveException ex) {
        return buildErrorResponse("FIRMWARE_NOT_ACTIVE", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(FirmwareAlreadyDeprecatedException.class)
    public ResponseEntity<ErrorResponse> handleFirmwareAlreadyDeprecated(FirmwareAlreadyDeprecatedException ex) {
        return buildErrorResponse("FIRMWARE_ALREADY_DEPRECATED", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return buildErrorResponse("PAYLOAD_TOO_LARGE", "Uploaded file exceeds the server upload limit.",
                HttpStatus.CONTENT_TOO_LARGE);
    }

    @ExceptionHandler(InvalidDeviceQrException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDeviceQr(InvalidDeviceQrException ex) {
        return buildErrorResponse("INVALID_DEVICE_QR", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DeviceAlreadyBoundException.class)
    public ResponseEntity<ErrorResponse> handleDeviceAlreadyBound(DeviceAlreadyBoundException ex) {
        return buildErrorResponse("DEVICE_ALREADY_BOUND", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DuplicateLedModelNameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateLedModelName(DuplicateLedModelNameException ex) {
        return buildErrorResponse("DUPLICATE_LED_MODEL_NAME", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(LedModelDimensionsLockedException.class)
    public ResponseEntity<ErrorResponse> handleLedModelDimensionsLocked(LedModelDimensionsLockedException ex) {
        return buildErrorResponse("LED_MODEL_DIMENSIONS_LOCKED", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DeviceNotBoundException.class)
    public ResponseEntity<ErrorResponse> handleDeviceNotBound(DeviceNotBoundException ex) {
        return buildErrorResponse("DEVICE_NOT_BOUND", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DeviceOwnershipMismatchException.class)
    public ResponseEntity<ErrorResponse> handleDeviceOwnershipMismatch(DeviceOwnershipMismatchException ex) {
        return buildErrorResponse("DEVICE_OWNERSHIP_MISMATCH", ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(WarrantyNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleWarrantyNotActive(WarrantyNotActiveException ex) {
        return buildErrorResponse("WARRANTY_NOT_ACTIVE", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(WarrantyExpiredException.class)
    public ResponseEntity<ErrorResponse> handleWarrantyExpired(WarrantyExpiredException ex) {
        return buildErrorResponse("WARRANTY_EXPIRED", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(WarrantyClaimConflictException.class)
    public ResponseEntity<ErrorResponse> handleWarrantyClaimConflict(WarrantyClaimConflictException ex) {
        return buildErrorResponse("WARRANTY_CLAIM_ALREADY_EXISTS", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(WarrantyClaimNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWarrantyClaimNotFound(WarrantyClaimNotFoundException ex) {
        return buildErrorResponse("WARRANTY_CLAIM_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidWarrantyTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidWarrantyTransition(InvalidWarrantyTransitionException ex) {
        return buildErrorResponse("INVALID_WARRANTY_STATUS_TRANSITION", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(OrderNotDeliveredYetException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotDeliveredYet(OrderNotDeliveredYetException ex) {
        return buildErrorResponse("ORDER_NOT_DELIVERED_YET", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(OrderStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleOrderStatusTransition(OrderStatusTransitionException ex) {
        return buildErrorResponse("INVALID_ORDER_STATUS_TRANSITION", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CarrierDispatchException.class)
    public ResponseEntity<ErrorResponse> handleCarrierDispatchException(CarrierDispatchException ex) {
        return buildErrorResponse(ex.getErrorCode(), ex.getMessage(), ex.getHttpStatus());
    }

    @ExceptionHandler(OrderFulfillmentException.class)
    public ResponseEntity<ErrorResponse> handleOrderFulfillmentException(OrderFulfillmentException ex) {
        return buildErrorResponse(ex.getErrorCode(), ex.getMessage(), ex.getHttpStatus());
    }

    // Payment exceptions
    @ExceptionHandler(PaymentSessionNotCancellableException.class)
    public ResponseEntity<ErrorResponse> handlePaymentSessionNotCancellable(
            PaymentSessionNotCancellableException ex) {
        return buildErrorResponse("PAYMENT_SESSION_NOT_CANCELLABLE", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PaymentSessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentSessionNotFound(PaymentSessionNotFoundException ex) {
        return buildErrorResponse("PAYMENT_SESSION_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    // IAM exceptions
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return buildErrorResponse("INVALID_CREDENTIALS", ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(InvalidManagedUserRoleException.class)
    public ResponseEntity<ErrorResponse> handleInvalidManagedUserRole(InvalidManagedUserRoleException ex) {
        return buildErrorResponse("INVALID_MANAGED_USER_ROLE", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException ex) {
        return buildErrorResponse("ACCOUNT_LOCKED", ex.getMessage(), HttpStatus.LOCKED);
    }

    @ExceptionHandler(AccountNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotVerified(AccountNotVerifiedException ex) {
        return buildErrorResponse("ACCOUNT_NOT_VERIFIED", ex.getMessage(), HttpStatus.FORBIDDEN);
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

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ErrorResponse.ErrorDetail> details = ex.getConstraintViolations().stream()
                .map(violation -> ErrorResponse.ErrorDetail.builder()
                        .field(violation.getPropertyPath().toString())
                        .message(violation.getMessage())
                        .build())
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

    // Commission exceptions
    @ExceptionHandler(PricingTierInvariantViolationException.class)
    public ResponseEntity<ErrorResponse> handlePricingTierInvariantViolation(PricingTierInvariantViolationException ex) {
        return buildErrorResponse("PRICING_INVARIANT_VIOLATION", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidRuleConfigurationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRuleConfiguration(InvalidRuleConfigurationException ex) {
        return buildErrorResponse("INVALID_RULE_CONFIGURATION", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CommissionRuleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommissionRuleNotFound(CommissionRuleNotFoundException ex) {
        return buildErrorResponse("COMMISSION_RULE_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CommissionTierNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommissionTierNotFound(CommissionTierNotFoundException ex) {
        return buildErrorResponse("COMMISSION_TIER_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(PricingRuleInUseException.class)
    public ResponseEntity<ErrorResponse> handlePricingRuleInUse(PricingRuleInUseException ex) {
        return buildErrorResponse("PRICING_RULE_IN_USE", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SystemRuleLockedException.class)
    public ResponseEntity<ErrorResponse> handleSystemRuleLocked(SystemRuleLockedException ex) {
        return buildErrorResponse("SYSTEM_RULE_LOCKED", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DuplicateCommissionRuleException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateCommissionRule(DuplicateCommissionRuleException ex) {
        return buildErrorResponse("DUPLICATE_COMMISSION_RULE", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CommissionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommissionNotFound(CommissionNotFoundException ex) {
        return buildErrorResponse("COMMISSION_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CommissionSubmissionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommissionSubmissionNotFound(CommissionSubmissionNotFoundException ex) {
        return buildErrorResponse("COMMISSION_SUBMISSION_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CommissionOwnershipMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCommissionOwnershipMismatch(CommissionOwnershipMismatchException ex) {
        return buildErrorResponse("COMMISSION_OWNERSHIP_MISMATCH", ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InvalidCommissionRuleSelectionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCommissionRuleSelection(InvalidCommissionRuleSelectionException ex) {
        return buildErrorResponse("INVALID_COMMISSION_RULE_SELECTION", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PresetSelectionMismatchException.class)
    public ResponseEntity<ErrorResponse> handlePresetSelectionMismatch(PresetSelectionMismatchException ex) {
        return buildErrorResponse("PRESET_SELECTION_MISMATCH", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CommissionStaffLockException.class)
    public ResponseEntity<ErrorResponse> handleCommissionStaffLock(CommissionStaffLockException ex) {
        return buildErrorResponse("COMMISSION_STAFF_LOCKED", ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InvalidCommissionStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCommissionStatusTransition(InvalidCommissionStatusTransitionException ex) {
        return buildErrorResponse("INVALID_COMMISSION_STATUS_TRANSITION", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidStaffAssignmentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStaffAssignment(InvalidStaffAssignmentException ex) {
        return buildErrorResponse("INVALID_STAFF_ASSIGNMENT", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidCommissionReassignmentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCommissionReassignment(InvalidCommissionReassignmentException ex) {
        return buildErrorResponse("INVALID_COMMISSION_REASSIGNMENT", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidDesignerAssignmentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDesignerAssignment(InvalidDesignerAssignmentException ex) {
        return buildErrorResponse("INVALID_DESIGNER_ASSIGNMENT", ex.getMessage(), HttpStatus.BAD_REQUEST);
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
