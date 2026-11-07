package com.algolearner.platform.profile.mock;

import com.algolearner.platform.profile.dashboard.InMemoryDashboardRepository;
import com.algolearner.platform.classroom.mock.InMemoryClassroomRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static com.algolearner.platform.profile.mock.MockData.id;

class PublishedStatisticsTest {
    private final Instant now = Instant.parse("2026-10-29T10:00:00Z");
    private final MockData data = new MockData(Clock.fixed(now, ZoneOffset.UTC));

    @Test void publishedProblemWithAcceptedOfficialSubmissionCountsAsSolved() {
        assertThat(statisticsForProblem("PUBLISHED").solved()).isEqualTo(1);
    }

    @Test void draftProblemWithAcceptedOfficialSubmissionDoesNotCountAsSolved() {
        assertThat(statisticsForProblem("DRAFT").solved()).isZero();
    }

    private com.algolearner.platform.profile.PracticeStatisticsService.Statistics statisticsForProblem(String status) {
        data.contents.clear();
        data.judge.clear();
        data.contents.add(new MockData.Content(id("problem"), id("teacher"), "PROBLEM", status, "APPROVED", now));
        data.judge.add(new MockData.Judge(id("student"), id("problem"), null, "ACCEPTED", true, now));
        return new com.algolearner.platform.profile.PracticeStatisticsService(data, Clock.fixed(now, ZoneOffset.UTC))
            .forAccount(id("student"));
    }

    @Test void acceptedSubmissionsRequirePublishedProblemAndOfficialAcceptedVerdict() {
        data.judge.add(new MockData.Judge(id("student"), id("content0"), null, "ACCEPTED", true, now));
        data.judge.add(new MockData.Judge(id("student"), id("missing"), null, "ACCEPTED", true, now));
        for (String status : new String[]{"UNPUBLISHED", "ARCHIVED"}) {
            data.contents.add(new MockData.Content(id(status), id("teacher"), "PROBLEM", status, "APPROVED", now));
            data.judge.add(new MockData.Judge(id("student"), id(status), null, "ACCEPTED", true, now));
        }
        assertThat(data.acceptedPublicSubmissions(id("student"))).hasSize(7)
            .allSatisfy(s -> assertThat(s.problemId()).isEqualTo(id("content1")));
    }

    @Test void adminPublicTotalsExcludeNonPublishedContent() {
        for (String status : new String[]{"UNPUBLISHED", "ARCHIVED"}) {
            for (String kind : new String[]{"COURSE", "PROBLEM"}) {
                data.contents.add(new MockData.Content(id(status + kind), id("teacher"), kind, status, "APPROVED", now));
            }
        }
        var totals = new InMemoryDashboardRepository(data, new InMemoryClassroomRepository(data)).totals();
        assertThat(totals.publicCourseCount()).isEqualTo(1);
        assertThat(totals.publicProblemCount()).isEqualTo(1);
        assertThat(totals.officialSubmissionCount()).isEqualTo(data.judge.stream().filter(MockData.Judge::official).count());
    }
}
