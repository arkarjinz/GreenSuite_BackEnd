package com.app.greensuitetest.exception;

import java.util.Map;

public class AuthException extends BaseException {
    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Map<String, Object> details) {
        super(message, details);
    }

    public AuthException(String message, Throwable cause, Map<String, Object> details) {
        super(message, cause, details);
    }
}