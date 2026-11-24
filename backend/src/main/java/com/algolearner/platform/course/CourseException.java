package com.algolearner.platform.course;

import com.algolearner.platform.common.exception.AppException;
import org.springframework.http.HttpStatus;

/** Stable SRS 4.11 error codes mapped by the global {@code ApiExceptionHandler}. */
public final class CourseException extends AppException {
    private CourseException(HttpStatus status, String code, String message) { super(message, status, code); }

    public static CourseException invalid(String message) {
        return new CourseException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }
    public static CourseException forbidden(String message) {
        return new CourseException(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", message);
    }
    public static CourseException notFound() {
        return new CourseException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Course not found");
    }
    public static CourseException notFound(String code, String message) {
        return new CourseException(HttpStatus.NOT_FOUND, code, message);
    }
    public static CourseException conflict(String code, String message) {
        return new CourseException(HttpStatus.CONFLICT, code, message);
    }
    public static CourseException unprocessable(String code, String message) {
        return new CourseException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
    public static CourseException locked() {
        return new CourseException(HttpStatus.FORBIDDEN, "COURSE_ACCESS_LOCKED", "Course content is locked by an administrator");
    }
    public static CourseException notEnrolled() {
        return new CourseException(HttpStatus.FORBIDDEN, "COURSE_NOT_ENROLLED", "Enroll in the course to access this content");
    }
}
