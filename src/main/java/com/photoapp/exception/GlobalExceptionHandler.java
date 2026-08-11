package com.photoapp.exception;

import com.photoapp.dto.ApiErrorResponse;
import com.photoapp.ratelimit.RateLimitExceededException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Centralized error handling. All error responses use the uniform envelope
 * {@code {"data": null, "error": "...", "timestamp": "..."}}. Stack traces
 * and internal details are never returned to the client; only generic,
 * non-sensitive messages are logged and returned.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountLocked(AccountLockedException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED).body(ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse.of("Invalid username or password"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiErrorResponse.of("Access denied"));
    }

    @ExceptionHandler(PhotoNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePhotoNotFound(PhotoNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedFileType(UnsupportedFileTypeException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiErrorResponse.of("Uploaded file exceeds the maximum allowed size"));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(EncryptionException.class)
    public ResponseEntity<ApiErrorResponse> handleEncryption(EncryptionException ex) {
        // Never include the cause's message: it might reference key material or IV details.
        log.error("Encryption/decryption operation failed");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of("A processing error occurred"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(Exception ex) {
        String message;
        if (ex instanceof MethodArgumentNotValidException manv) {
            message = manv.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining("; "));
        } else {
            message = "Invalid request";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(message.isBlank() ? "Invalid request" : message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception of type {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of("An unexpected error occurred"));
    }
}
