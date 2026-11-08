package com.algolearner.platform.common.exception;

import com.algolearner.platform.common.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        return error(ErrorCode.VALIDATION_FAILED, request, fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    ResponseEntity<ApiErrorResponse> invalid(Exception ex, HttpServletRequest request) {
        return error(ErrorCode.INVALID_REQUEST_BODY, request, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> tooLarge(Exception ex, HttpServletRequest request) {
        return error(ErrorCode.AVATAR_TOO_LARGE, request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> forbidden(Exception ex, HttpServletRequest request) {
        return error(ErrorCode.FORBIDDEN_OPERATION, request, null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> status(ResponseStatusException ex, HttpServletRequest request) {
        return error(HttpStatus.valueOf(ex.getStatusCode().value()), ErrorCode.INVALID_REQUEST.getCode(),
                ErrorCode.INVALID_REQUEST.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse frameworkError) {
            HttpStatus status = HttpStatus.valueOf(frameworkError.getStatusCode().value());
            return error(status, "HTTP_" + status.value(), status.getReasonPhrase(), request, null);
        }
        LOG.error("Request failed: {}", request.getRequestURI(), ex);
        return error(ErrorCode.INTERNAL_SERVER_ERROR, request, null);
    }

    private ResponseEntity<ApiErrorResponse> error(ErrorCode error, HttpServletRequest request,
            List<ApiErrorResponse.ValidationError> fields) {
        return error(error.getHttpStatus(), error.getCode(), error.getMessage(), request, fields);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message,
            HttpServletRequest request, List<ApiErrorResponse.ValidationError> fields) {
        return ResponseEntity.status(status).body(ApiErrorResponse.builder().errorCode(code).message(message)
                .path(request.getRequestURI()).errors(fields).build());
    }
}