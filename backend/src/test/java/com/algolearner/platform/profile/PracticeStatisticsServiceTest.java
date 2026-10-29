package com.algolearner.platform.profile;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PracticeStatisticsServiceTest {
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-29T18:00:00Z"),ZoneOffset.UTC);
    @Test void emptyHistory() {
        var stats=PracticeStatisticsService.calculate(List.of(),clock);
        assertThat(stats.solved()).isZero();
        assertThat(stats.streak()).isEqualTo(new PracticeStatisticsService.Streak(0,0));
    }
    @Test void vietnamMidnightDeduplicationAndFutureExclusion() {
        UUID problem=UUID.randomUUID();
        var stats=PracticeStatisticsService.calculate(List.of(
                new PracticeRepository.Accepted(problem,Instant.parse("2026-10-28T16:59:59Z")),
                new PracticeRepository.Accepted(problem,Instant.parse("2026-10-28T17:00:00Z")),
                new PracticeRepository.Accepted(problem,Instant.parse("2026-10-29T17:00:00Z")),
                new PracticeRepository.Accepted(UUID.randomUUID(),clock.instant().plusSeconds(1))),clock);
        assertThat(stats.solved()).isEqualTo(1);
        assertThat(stats.streak()).isEqualTo(new PracticeStatisticsService.Streak(3,3));
    }
    @Test void yesterdayKeepsCurrentStreakButOlderHistoryDoesNot() {
        var yesterday=new PracticeRepository.Accepted(UUID.randomUUID(),Instant.parse("2026-10-28T18:00:00Z"));
        assertThat(PracticeStatisticsService.calculate(List.of(yesterday),clock).streak().currentStreak()).isEqualTo(1);
        var older=new PracticeRepository.Accepted(UUID.randomUUID(),Instant.parse("2026-10-27T16:00:00Z"));
        assertThat(PracticeStatisticsService.calculate(List.of(older),clock).streak()).isEqualTo(new PracticeStatisticsService.Streak(0,1));
    }
    @Test void repositoryIsScopedToRequestedAccount() {
        var repository=mock(PracticeRepository.class);
        UUID id=UUID.randomUUID();
        when(repository.acceptedPublicSubmissions(id)).thenReturn(List.of());
        assertThat(new PracticeStatisticsService(repository,clock).forAccount(id).solved()).isZero();
        verify(repository).acceptedPublicSubmissions(id);
        verifyNoMoreInteractions(repository);
    }
}
