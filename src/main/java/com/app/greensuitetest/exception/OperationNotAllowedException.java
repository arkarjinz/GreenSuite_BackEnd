package com.app.greensuitetest.exception;

import java.util.Map;

public class OperationNotAllowedException extends BaseException {
    public OperationNotAllowedException(String message) {
        super(message);
    }

    public OperationNotAllowedException(String message, Map<String, Object> details) {
        super(message, details);
    }
}