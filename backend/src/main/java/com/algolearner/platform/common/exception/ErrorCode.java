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
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email is already registered"),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Username is already taken"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username/email or password"),
    ACCOUNT_PENDING_VERIFICATION(HttpStatus.FORBIDDEN, "Account email is not verified yet"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Account is temporarily locked due to multiple failed login attempts"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "Account has been suspended"),
    ACCOUNT_DEACTIVATED(HttpStatus.FORBIDDEN, "Account has been deactivated"),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "Invalid OTP code"),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "OTP code has expired"),
    OTP_MAX_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Maximum OTP attempts exceeded. Please request a new OTP"),
    OTP_COOLDOWN_ACTIVE(HttpStatus.TOO_MANY_REQUESTS, "Please wait 60 seconds before requesting another OTP"),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token"),
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters and contain at least one letter and one number"),
    TERMS_NOT_ACCEPTED(HttpStatus.BAD_REQUEST, "Terms of service and privacy policy must be accepted"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Session not found"),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "Session has been revoked"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token has expired"),
    AVATAR_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Avatar exceeds upload limit"),
    UNSUPPORTED_AVATAR_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Avatar must be a supported image"),
    FILE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Avatar could not be stored"),
    FEATURE_DATA_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Feature data is not configured"),
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
