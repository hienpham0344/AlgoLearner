package com.algolearner.platform.profile.dashboard;

import com.algolearner.platform.profile.PracticeStatisticsService;
import com.algolearner.platform.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {
    final DashboardRepository repository=mock(DashboardRepository.class);
    final DashboardService service=new DashboardService(repository,mock(PracticeStatisticsService.class),Clock.systemUTC());
    @Test void invalidRangeRejectedBeforeRepositoryRead() {
        Instant now=Instant.parse("2026-10-29T10:00:00Z");
        assertThatThrownBy(() -> service.charts(now,now,"day")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.charts(now,now.plus(Duration.ofDays(367)),"day")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.charts(now,now.plusSeconds(1),"year")).isInstanceOf(ApiException.class);
        verifyNoInteractions(repository);
    }
    @Test void dayBucketsUseVietnamMidnightAndKeepPartialEdges() {
        Instant start=Instant.parse("2026-10-28T16:00:00Z"),end=Instant.parse("2026-10-29T18:00:00Z");
        when(repository.chartEvents(start,end)).thenReturn(List.of(
                new DashboardRepository.ChartEvent(start,"STUDENT",2),
                new DashboardRepository.ChartEvent(Instant.parse("2026-10-28T17:00:00Z"),"SUBMISSION",3),
                new DashboardRepository.ChartEvent(end,"SUBMISSION",99)));
        var chart=service.charts(start,end,"day");
        assertThat(chart.items()).hasSize(3);
        assertThat(chart.items().getFirst().startsAt()).isEqualTo(start);
        assertThat(chart.items().getFirst().endsAt()).isEqualTo(Instant.parse("2026-10-28T17:00:00Z"));
        assertThat(chart.items().getLast().endsAt()).isEqualTo(end);
        assertThat(chart.items().getFirst().studentCreatedCount()).isEqualTo(2);
        assertThat(chart.items().get(1).officialSubmissionCount()).isEqualTo(3);
        assertThat(chart.items().getLast().officialSubmissionCount()).isZero();
    }
}
