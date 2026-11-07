package com.algolearner.platform.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** API error contract: stable machine code, HTTP status and default public message. */
@Getter
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Invalid request parameters"),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "Invalid request parameters"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request parameters"),
    UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    FORBIDDEN_OPERATION(HttpStatus.FORBIDDEN, "Access denied"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Profile not found"),
    CLASSROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "Classroom not found"),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Classroom invitation not found"),
    CLASSROOM_CONFLICT(HttpStatus.CONFLICT, "Classroom operation conflicts with its current state"),
    CLASSROOM_JOIN_DENIED(HttpStatus.CONFLICT, "The classroom cannot be joined"),
    INVITATION_EXPIRED(HttpStatus.CONFLICT, "Classroom invitation is no longer valid"),
    AVATAR_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Avatar exceeds upload limit"),
    UNSUPPORTED_AVATAR_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Avatar must be a supported image"),
    FILE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Avatar could not be stored"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Request could not be completed");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String getCode() {
        return name();
    }
}
