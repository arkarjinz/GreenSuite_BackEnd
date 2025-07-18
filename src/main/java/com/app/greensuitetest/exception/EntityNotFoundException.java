package com.app.greensuitetest.exception;

import java.util.Map;

public class EntityNotFoundException extends BaseException {
    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Map<String, Object> details) {
        super(message, details);
    }
}