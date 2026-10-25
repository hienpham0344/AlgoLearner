package com.algolearner.platform.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@Schema(description = "Standard paginated API response wrapper")
public class PageResponse<T> {

    @Schema(description = "Indicates whether the request was successful", example = "true")
    @Builder.Default
    private final boolean success = true;

    @Schema(description = "Paginated payload data")
    private final PaginatedData<T> data;

    @Schema(description = "Response timestamp in ISO-8601 UTC format", example = "2026-10-25T14:30:00Z")
    @Builder.Default
    private final Instant timestamp = Instant.now();

    @Getter
    @Builder
    @Schema(description = "Pagination metadata and item list")
    public static class PaginatedData<T> {
        @Schema(description = "List of items in the current page")
        private final List<T> items;

        @Schema(description = "Current page number (0-indexed)", example = "0")
        private final int page;

        @Schema(description = "Page size (number of elements per page)", example = "20")
        private final int size;

        @Schema(description = "Total number of elements across all pages", example = "100")
        private final long totalElements;

        @Schema(description = "Total number of pages", example = "5")
        private final int totalPages;

        @Schema(description = "Is current page the first page?", example = "true")
        private final boolean isFirst;

        @Schema(description = "Is current page the last page?", example = "false")
        private final boolean isLast;
    }

    public static <T> PageResponse<T> fromPage(Page<T> springPage) {
        PaginatedData<T> paginatedData = PaginatedData.<T>builder()
                .items(springPage.getContent())
                .page(springPage.getNumber())
                .size(springPage.getSize())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .isFirst(springPage.isFirst())
                .isLast(springPage.isLast())
                .build();

        return PageResponse.<T>builder()
                .data(paginatedData)
                .build();
    }
}
