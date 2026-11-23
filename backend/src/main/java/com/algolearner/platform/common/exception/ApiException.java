package com.algolearner.platform.common.exception;

public class ApiException extends AppException {
    public ApiException(ErrorCode error) {
        this(error, error.getMessage());
    }

    public ApiException(ErrorCode error, String message) {
        super(error, message);
    }

    public ApiException(ErrorCode error, String message, long retryAfterSeconds) {
        super(error, message, retryAfterSeconds);
    }

    public static ApiException withRetryAfter(ErrorCode error, long retryAfterSeconds) {
        return new ApiException(error, error.getMessage(), retryAfterSeconds);
    }
}
