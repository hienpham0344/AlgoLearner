package com.algolearner.platform.storage;

import com.algolearner.platform.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class StorageException extends AppException {
    public StorageException(String code, HttpStatus status, String message) {
        super(message, status, code);
    }
}
