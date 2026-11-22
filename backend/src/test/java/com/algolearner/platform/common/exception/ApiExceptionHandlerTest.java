package com.algolearner.platform.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/example");

    @Test void validationUsesCatalogAndPreservesFieldDetails() {
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "email", "Email is invalid"));
        var response = handler.validation(new MethodArgumentNotValidException(null, binding), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.getMessage()).isEqualTo("Invalid request parameters");
        assertThat(body.getPath()).isEqualTo("/api/v1/example");
        assertThat(body.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getField()).isEqualTo("email");
            assertThat(e.getMessage()).isEqualTo("Email is invalid");
        });
    }

    @Test void applicationExceptionUsesCatalogWithOptionalBusinessMessage() {
        var response = handler.app(new ApiException(ErrorCode.PROFILE_NOT_FOUND), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().containsKey("Retry-After")).isFalse();
        assertThat(response.getBody().getErrorCode()).isEqualTo("PROFILE_NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("Profile not found");
        assertThat(handler.app(new ApiException(ErrorCode.VALIDATION_FAILED, "Custom detail"), request)
            .getBody().getMessage()).isEqualTo("Custom detail");
    }

    @Test void applicationExceptionWithRetryAfterEmitsHeaderAndPreservesErrorBody() {
        var response = handler.app(ApiException.withRetryAfter(ErrorCode.ACCOUNT_TEMP_LOCKED, 900), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("900");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("ACCOUNT_TEMP_LOCKED");
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.ACCOUNT_TEMP_LOCKED.getMessage());
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/example");
    }

    @Test void responseStatusRetainsHttpStatusWithoutExposingInternalReason() {
        var response = handler.status(new ResponseStatusException(HttpStatus.CONFLICT, "Internal detail"), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.INVALID_REQUEST.getMessage());
    }

    @Test void unexpectedFailureReturnsSafeCatalogMessage() {
        var response = handler.unexpected(new RuntimeException("secret"), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Request could not be completed");
    }

    @Test void unexpectedExceptionDetailsAreNotWrittenToApplicationLogs() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();logger.addAppender(appender);
        try {
            handler.unexpected(new RuntimeException("private-provider-credential"),request);
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("private-provider-credential");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);appender.stop();
        }
    }

    @Test void learningFileMultipartLimitUsesFileErrorCode() {
        var upload = new MockHttpServletRequest("POST","/api/v1/classrooms/00000000-0000-0000-0000-000000000001/files");
        var response = handler.tooLarge(new org.springframework.web.multipart.MaxUploadSizeExceededException(20L*1024*1024),upload);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().getErrorCode()).isEqualTo("FILE_TOO_LARGE");
    }
}
