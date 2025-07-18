package com.app.greensuitetest.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class ValidationException extends BaseException {
    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Map<String, Object> details) {
        super(message, details);
    }

    public ValidationException(String message, String key, Object value) {
        super(message, Map.of(key, value));
    }
}