package com.example.workspace.reservation.exception;

public class ConflictException extends RuntimeException {
    private final String errorCode;

    public ConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ConflictException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

