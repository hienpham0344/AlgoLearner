package com.algolearner.platform.common.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends AppException {
    public ApiException(HttpStatus status, String code, String message) { super(message, status, code); }
}
