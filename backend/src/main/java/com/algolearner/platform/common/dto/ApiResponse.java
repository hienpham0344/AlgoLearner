package com.algolearner.platform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response envelope")
public class ApiResponse<T> {

    @Schema(description = "Indicates whether the request was successful", example = "true")
    private final boolean success;

    @Schema(description = "Human-readable status or feedback message", example = "Operation completed successfully")
    private final String message;

    @Schema(description = "Response payload data")
    private final T data;

    @Schema(description = "Response timestamp in ISO-8601 UTC format", example = "2026-10-25T14:30:00Z")
    @Builder.Default
    private final Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> messageOnly(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }
}
