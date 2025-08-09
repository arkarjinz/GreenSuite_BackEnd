package com.app.greensuitetest.exception;

import java.util.Map;

public class InsufficientCreditsException extends RuntimeException {
    private final Map<String, Object> details;

    public InsufficientCreditsException(String message) {
        super(message);
        this.details = Map.of();
    }

    public InsufficientCreditsException(String message, Map<String, Object> details) {
        super(message);
        this.details = details;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}