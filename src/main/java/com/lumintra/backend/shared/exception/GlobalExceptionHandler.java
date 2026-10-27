package com.lumintra.backend.shared.exception;

import com.lumintra.backend.shared.exception.iam.AccountLockedException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.EmailAlreadyRegisteredException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Tracer tracer;

    @ExceptionHandler(com.lumintra.backend.shared.exception.iam.AuthFlowException.class)
    public ResponseEntity<ErrorResponse> handleAuthFlow(com.lumintra.backend.shared.exception.iam.AuthFlowException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "ACCOUNT_DISABLED", "MANAGEMENT_ACCESS_DENIED", "GOOGLE_LINK_REQUIRED", "ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            case "GOOGLE_UNAVAILABLE", "AUTH_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.UNAUTHORIZED;
        };
        return buildErrorResponse(ex.getCode(), ex.getMessage(), status);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        return buildErrorResponse("EMAIL_ALREADY_REGISTERED", ex.getMessage(), HttpStatus.CONFLICT,
                List.of(ErrorResponse.ErrorDetail.builder().field("email").message(ex.getMessage()).build()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> ErrorResponse.ErrorDetail.builder()
                        .field(switch (error.getField()) {
                            case "passwordsMatch" -> "confirmPassword";
                            case "passwordWithinLimit" -> "password";
                            default -> error.getField();
                        })
                        .message(error.getDefaultMessage()).build()).toList();
        return buildErrorResponse("VALIDATION_FAILED", "Vui lòng kiểm tra thông tin đã nhập.", HttpStatus.BAD_REQUEST, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException ex) {
        return buildErrorResponse("INVALID_REQUEST", "Dữ liệu yêu cầu không hợp lệ.", HttpStatus.BAD_REQUEST);
    }

    // The database remains authoritative when concurrent registrations pass the pre-check.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataConflict(DataIntegrityViolationException ex) {
        return buildErrorResponse("DATA_CONFLICT", "Dữ liệu đã tồn tại hoặc không còn hợp lệ. Vui lòng kiểm tra lại.", HttpStatus.CONFLICT);
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

    // General fallback
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unexpected system failure occurred", exception);
        return buildErrorResponse("INTERNAL_SERVER_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(String code, String message, HttpStatus status) {
        return buildErrorResponse(code, message, status, List.of());
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(String code, String message, HttpStatus status,
                                                           List<ErrorResponse.ErrorDetail> details) {
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
