package com.app.greensuitetest.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.Map;

@Getter
public class BaseException extends RuntimeException {
    private final Map<String, Object> details;

    public BaseException(String message) {
        super(message);
        this.details = Collections.emptyMap();
    }

    public BaseException(String message, Map<String, Object> details) {
        super(message);
        this.details = details;
    }

    public BaseException(String message, Throwable cause, Map<String, Object> details) {
        super(message, cause);
        this.details = details;
    }
}