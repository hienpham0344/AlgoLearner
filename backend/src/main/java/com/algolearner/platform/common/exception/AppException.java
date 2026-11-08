package com.algolearner.platform.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class AppException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    public AppException(ErrorCode error) {
        this(error, error.getMessage());
    }

    public AppException(ErrorCode error, String message) {
        super(message);
        this.httpStatus = error.getHttpStatus();
        this.errorCode = error.getCode();
    }

    public AppException(ErrorCode error, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = error.getHttpStatus();
        this.errorCode = error.getCode();
    }
}
