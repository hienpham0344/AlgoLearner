package com.algolearner.platform.common.exception;

public class ApiException extends AppException {
    public ApiException(ErrorCode error) {
        this(error, error.getMessage());
    }

    public ApiException(ErrorCode error, String message) {
        super(error, message);
    }
}
