package com.app.greensuitetest.dto;

import java.time.Instant;
import java.util.Map;

public record ApiResponse(
        String status,
        String message,
        Object data,
        String timestamp
) {
    public ApiResponse(String status, String message, Object data) {
        this(status, message, data, Instant.now().toString());
    }

    public static ApiResponse success(String message) {
        return new ApiResponse("success", message, null);
    }

    public static ApiResponse success(String message, Object data) {
        return new ApiResponse("success", message, data);
    }

    public static ApiResponse error(String message) {
        return new ApiResponse("error", message, null);
    }

    public static ApiResponse error(String message, Object data) {
        return new ApiResponse("error", message, data);
    }

    public static ApiResponse validationError(String message, Map<String, String> errors) {
        return new ApiResponse("validation_error", message, errors);
    }

    public static ApiResponse conflictError(String message, Object details) {
        return new ApiResponse("conflict", message, details);
    }
}