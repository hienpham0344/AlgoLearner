package com.algolearner.platform.problem;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.common.exception.AppException;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

class ProblemCatalogSecurityTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final ProblemCatalogService catalog =
            new ProblemCatalogService(jdbc, mock(ProblemService.class), mock(AuditService.class));

    @Test
    void rejectsOversizedSearchBeforeQueryingDatabase() {
        assertThatThrownBy(
                        () ->
                                catalog.browse(
                                        UUID.randomUUID(),
                                        "x".repeat(256),
                                        null,
                                        null,
                                        ProblemDtos.Order.NEWEST,
                                        PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(
                        () ->
                                catalog.browse(
                                        UUID.randomUUID(),
                                        null,
                                        null,
                                        "x".repeat(81),
                                        ProblemDtos.Order.NEWEST,
                                        PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void openReportQuotaRejectsBeforeInsert() {
        when(jdbc.queryForObject(contains("created_at>"), anyMap(), eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForObject(contains("status IN"), anyMap(), eq(Long.class))).thenReturn(10L);
        assertThatThrownBy(
                        () ->
                                catalog.report(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        new ProblemDtos.ReportRequest(
                                                ProblemDtos.ReportReason.OTHER, "Issue")))
                .isInstanceOfSatisfying(
                        AppException.class,
                        error -> assertThat(error.getHttpStatus().value()).isEqualTo(429));
        verify(jdbc, never()).update(anyString(), anyMap());
    }

    @Test
    void rollingReportQuotaRejectsEvenIfReportsAreClosed() {
        when(jdbc.queryForObject(contains("created_at>"), anyMap(), eq(Long.class))).thenReturn(5L);
        when(jdbc.queryForObject(contains("status IN"), anyMap(), eq(Long.class))).thenReturn(0L);
        assertThatThrownBy(
                        () ->
                                catalog.report(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        new ProblemDtos.ReportRequest(
                                                ProblemDtos.ReportReason.OTHER, "Issue")))
                .isInstanceOfSatisfying(
                        AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("REPORT_RATE_LIMIT"));
        verify(jdbc, never()).update(anyString(), anyMap());
    }
}
