package com.algolearner.platform.common.exception;

import com.algolearner.platform.common.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(AppException.class)
    ResponseEntity<ApiErrorResponse> app(AppException ex, HttpServletRequest request) {
        return error(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), request, null);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var fields = ex.getBindingResult().getFieldErrors().stream().map(e -> ApiErrorResponse.ValidationError.builder()
                .field(e.getField()).message(e.getDefaultMessage()).build()).toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request parameters", request, fields);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    ResponseEntity<ApiErrorResponse> invalid(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "Invalid request parameters", request, null);
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> tooLarge(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "AVATAR_TOO_LARGE", "Avatar exceeds upload limit", request, null);
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> forbidden(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", "Access denied", request, null);
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> status(ResponseStatusException ex, HttpServletRequest request) {
        return error(HttpStatus.valueOf(ex.getStatusCode().value()), "INVALID_REQUEST", ex.getReason(), request, null);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse frameworkError) {
            HttpStatus status=HttpStatus.valueOf(frameworkError.getStatusCode().value());
            return error(status,"HTTP_"+status.value(),status.getReasonPhrase(),request,null);
        }
        LOG.error("Request failed: {}", request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Request could not be completed", request, null);
    }
    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message,
            HttpServletRequest request, List<ApiErrorResponse.ValidationError> fields) {
        return ResponseEntity.status(status).body(ApiErrorResponse.builder().errorCode(code).message(message)
                .path(request.getRequestURI()).errors(fields).build());
    }
}
