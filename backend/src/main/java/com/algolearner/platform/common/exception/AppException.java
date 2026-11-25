package com.algolearner.platform.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class AppException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final Long retryAfterSeconds;

    public AppException(ErrorCode error) {
        this(error, error.getMessage());
    }

    public AppException(ErrorCode error, String message) {
        super(message);
        this.httpStatus = error.getHttpStatus();
        this.errorCode = error.getCode();
        this.retryAfterSeconds = null;
    }

    public AppException(ErrorCode error, String message, long retryAfterSeconds) {
        super(message);
        this.httpStatus = error.getHttpStatus();
        this.errorCode = error.getCode();
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public AppException(ErrorCode error, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = error.getHttpStatus();
        this.errorCode = error.getCode();
        this.retryAfterSeconds = null;
    }

    public AppException(String message, HttpStatus httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.retryAfterSeconds = null;
    }
}
