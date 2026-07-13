package com.pizza.api;

import com.pizza.api.dto.ApiResponses.ApiError;
import com.pizza.exception.CloudinaryException;
import com.pizza.exception.DuplicateEmailException;
import com.pizza.exception.DuplicatePhoneException;
import com.pizza.exception.InvalidCredentialsException;
import com.pizza.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * JSON counterpart of {@link com.pizza.exception.GlobalExceptionHandler}, scoped to
 * the API package so the two advices never compete: the HTML one keeps rendering
 * error.html for the server-rendered controllers, this one always returns JSON.
 * The status mapping is deliberately identical.
 */
@RestControllerAdvice(basePackages = "com.pizza.api")
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({DuplicateEmailException.class, DuplicatePhoneException.class})
    public ResponseEntity<ApiError> handleDuplicate(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleAuth(RuntimeException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /**
     * MethodArgumentNotValidException extends BindException, so this single handler
     * covers both @Valid @RequestBody and @Valid @ModelAttribute (multipart) forms.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleValidation(BindException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getFieldErrors()) {
            // Keep the first message per field; the client renders one .invalid-feedback.
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Please correct the highlighted fields.", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(CloudinaryException.class)
    public ResponseEntity<ApiError> handleCloudinary(CloudinaryException ex) {
        log.error("Cloudinary error", ex);
        return build(HttpStatus.BAD_GATEWAY,
                "We could not process the pizza image right now. Please try again.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadSize(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "The uploaded image is too large. Maximum allowed size is 5MB.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDatabase(DataAccessException ex) {
        log.error("Database error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "A database error occurred. Please try again later.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled API error on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Please try again later.");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
