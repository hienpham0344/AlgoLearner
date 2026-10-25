package com.algolearner.platform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API error response envelope")
public class ApiErrorResponse {

    @Schema(description = "Always false for error responses", example = "false")
    @Builder.Default
    private final boolean success = false;

    @Schema(description = "Machine-readable business error code", example = "RESOURCE_NOT_FOUND")
    private final String errorCode;

    @Schema(description = "Human-readable explanation of the error", example = "The requested classroom was not found")
    private final String message;

    @Schema(description = "Detailed field validation errors if applicable")
    private final List<ValidationError> errors;

    @Schema(description = "Endpoint URI path where the error occurred", example = "/api/v1/classrooms/c123")
    private final String path;

    @Schema(description = "Error timestamp in ISO-8601 UTC format", example = "2026-10-25T14:30:00Z")
    @Builder.Default
    private final Instant timestamp = Instant.now();

    @Getter
    @Builder
    @Schema(description = "Specific validation failure on a field")
    public static class ValidationError {
        @Schema(description = "Field name that failed validation", example = "email")
        private final String field;

        @Schema(description = "Rejected value submitted by client", example = "invalid-email")
        private final Object rejectedValue;

        @Schema(description = "Validation failure reason", example = "Email must be a well-formed address")
        private final String message;
    }
}
