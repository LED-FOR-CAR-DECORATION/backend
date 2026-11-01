package com.lumintra.backend.shared.exception;

import com.lumintra.backend.shared.exception.iam.AccountLockedException;
import com.lumintra.backend.shared.exception.iam.EmailAlreadyRegisteredException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Tracer tracer;

    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleMissingParameter(Exception ex) {
        return buildErrorResponse("INVALID_REQUEST", "Required request parameters are missing or invalid.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.lumintra.backend.firmware.domain.model.FirmwareException.class)
    public ResponseEntity<ErrorResponse> handleFirmware(com.lumintra.backend.firmware.domain.model.FirmwareException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "FIRMWARE_FORBIDDEN", "FIRMWARE_SESSION_INVALID" -> HttpStatus.FORBIDDEN;
            case "FIRMWARE_NOT_FOUND", "FIRMWARE_DEVICE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "FIRMWARE_CONFLICT", "FIRMWARE_REVOKED", "FIRMWARE_UNAVAILABLE" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return buildErrorResponse(ex.getCode(), ex.getMessage(), status);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return buildErrorResponse("ACCESS_DENIED", "Access is denied.", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return buildErrorResponse("FILE_TOO_LARGE", "The uploaded file exceeds the allowed size.", HttpStatus.PAYLOAD_TOO_LARGE);
    }



    // IAM
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

    // General
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