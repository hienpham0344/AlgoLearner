package com.algolearner.platform.profile;

import org.springframework.stereotype.Service;
import java.time.*;
import com.algolearner.platform.profile.PracticeRepository.Accepted;
import java.util.*;

@Service
public class PracticeStatisticsService {
    public record Streak(int currentStreak, int highestStreak) { }
    public record Statistics(long solved, Streak streak) { }
    private final PracticeRepository repository;
    private final Clock clock;
    public PracticeStatisticsService(PracticeRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }
    public Statistics forAccount(UUID accountId) {
        var rows = repository.acceptedPublicSubmissions(accountId);
        return calculate(rows, clock);
    }

    static Statistics calculate(List<Accepted> submissions, Clock clock) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Set<UUID> solved = new HashSet<>();
        SortedSet<LocalDate> days = new TreeSet<>();
        for (Accepted item : submissions) {
            LocalDate day = item.submittedAt().atZone(zone).toLocalDate();
            if (!item.submittedAt().isAfter(clock.instant())) { solved.add(item.problemId()); days.add(day); }
        }
        int run = 0, highest = 0; LocalDate previous = null;
        for (LocalDate day : days) {
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            highest = Math.max(highest, run); previous = day;
        }
        int current = previous != null && !previous.isBefore(today.minusDays(1)) ? run : 0;
        return new Statistics(solved.size(), new Streak(current, highest));
    }
}
