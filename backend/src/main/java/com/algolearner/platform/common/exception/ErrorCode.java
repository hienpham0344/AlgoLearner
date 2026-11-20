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
    ACCOUNT_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email or username already exists"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    ACCOUNT_TEMP_LOCKED(HttpStatus.LOCKED, "Account is temporarily locked"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "Account is suspended"),
    INVALID_OTP(HttpStatus.BAD_REQUEST, "Invalid or expired verification code"),
    OTP_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Wait before requesting another code"),
    MAIL_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is not configured"),
    MAIL_DELIVERY_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "Verification email could not be sent"),
    AVATAR_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Avatar exceeds upload limit"),
    UNSUPPORTED_AVATAR_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Avatar must be a supported image"),
    FILE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Avatar could not be stored"),
    FEATURE_DATA_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Feature data is not configured"),
    ANNOUNCEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Announcement not found"),
    LESSON_NOT_FOUND(HttpStatus.NOT_FOUND, "Lesson not found"),
    CANNOT_HARD_DELETE_LESSON(HttpStatus.BAD_REQUEST, "Cannot permanently delete lesson with existing student progress or submissions; use archive instead"),
    QUIZ_ALREADY_LINKED(HttpStatus.CONFLICT, "Quiz is already linked to another lesson"),
    ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Assignment not found"),
    ASSIGNMENT_CLOSED(HttpStatus.BAD_REQUEST, "Assignment is closed for submissions"),
    ASSIGNMENT_NOT_STARTED(HttpStatus.BAD_REQUEST, "Assignment has not started yet"),
    ATTEMPT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Maximum number of attempts reached"),
    ATTEMPT_ALREADY_SUBMITTED(HttpStatus.BAD_REQUEST, "This attempt has already been submitted"),
    NO_ACTIVE_ATTEMPT(HttpStatus.BAD_REQUEST, "No active attempt found for this assignment"),
    TIME_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Time limit for this attempt has expired"),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Question not found"),
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
