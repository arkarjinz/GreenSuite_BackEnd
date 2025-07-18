package com.app.greensuitetest.exception;

import com.app.greensuitetest.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        String message = "Validation failed for " + errors.size() + " field(s)";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage
                ));

        String message = "Request validation failed for " + errors.size() + " parameter(s)";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, errors));
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse> handleCustomExceptions(BaseException ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String logMessage = "Error occurred";

        if (ex instanceof AuthException) {
            status = HttpStatus.UNAUTHORIZED;
            logMessage = "Authentication error";
        } else if (ex instanceof ValidationException) {
            status = HttpStatus.BAD_REQUEST;
            logMessage = "Validation error";
        } else if (ex instanceof EntityNotFoundException) {
            status = HttpStatus.NOT_FOUND;
            logMessage = "Entity not found";
        } else if (ex instanceof OperationNotAllowedException) {
            status = HttpStatus.FORBIDDEN;
            logMessage = "Operation not allowed";
        }

        log.warn("{}: {}", logMessage, ex.getMessage());

        // Create response details
        Map<String, Object> responseDetails = new HashMap<>();
        responseDetails.put("error", ex.getClass().getSimpleName());
        responseDetails.put("message", ex.getMessage());
        if (!ex.getDetails().isEmpty()) {
            responseDetails.put("details", ex.getDetails());
        }

        return ResponseEntity.status(status)
                .body(ApiResponse.error(ex.getMessage(), responseDetails));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGlobalException(Exception ex) {
        log.error("Internal Server Error: {}", ex.getMessage(), ex);

        Map<String, String> details = new HashMap<>();
        details.put("error", ex.getClass().getName());
        details.put("message", "An unexpected error occurred");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error", details));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse> handleDuplicateKeyException(DuplicateKeyException ex) {
        log.warn("Duplicate key violation: {}", ex.getMessage());

        // Try to extract field name from error message
        String errorMessage = ex.getMessage().toLowerCase();
        String field = "unknown";

        if (errorMessage.contains("email")) field = "email";
        else if (errorMessage.contains("user_name") || errorMessage.contains("username")) field = "userName";
        else if (errorMessage.contains("company.name") || errorMessage.contains("name_1")) field = "companyName";

        Map<String, Object> details = Map.of(
                "field", field,
                "error", ex.getClass().getSimpleName(),
                "message", "Duplicate value detected",
                "solution", "Please provide a unique value"
        );

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Duplicate key violation", details));
    }
}