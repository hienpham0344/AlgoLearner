package com.algolearner.platform.profile.dashboard;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.profile.PracticeStatisticsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static com.algolearner.platform.profile.dashboard.DashboardResponse.*;

@Service
public class DashboardService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final DashboardRepository repository;
    private final PracticeStatisticsService practice;
    private final Clock clock;

    public DashboardService(DashboardRepository repository, PracticeStatisticsService practice, Clock clock) {
        this.repository=repository; this.practice=practice; this.clock=clock;
    }

    @PreAuthorize("hasRole('STUDENT')")
    public Student student(UUID account, int previewSize, int upcomingDays) {
        var context=context(account,previewSize,upcomingDays);
        var statistics=practice.forAccount(account);
        return new Student("STUDENT",context.now(),ZONE.getId(),repository.studentClassrooms(context),
                repository.studentAssignments(context),repository.studentQuizzes(context),repository.courses(context),
                new Practice(statistics.solved()),new Streak(statistics.streak().currentStreak(),statistics.streak().highestStreak()),
                repository.notifications(context));
    }

    @PreAuthorize("hasRole('TEACHER')")
    public Teacher teacher(UUID account,int previewSize,int upcomingDays) {
        var context=context(account,previewSize,upcomingDays);
        return new Teacher("TEACHER",context.now(),ZONE.getId(),repository.teacherClassrooms(context),
                repository.teacherAssignments(context),repository.ungraded(context),repository.recentSubmissions(context),
                repository.notifications(context),repository.contributions(context,false),repository.classStatistics(context));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Admin admin(UUID account,int previewSize,Instant from,Instant to,String interval) {
        var context=context(account,previewSize,7);
        Instant end=to==null ? context.now() : to;
        Instant start=from==null ? end.minus(Duration.ofDays(30)) : from;
        // Validate and compute charts before running the other aggregate queries.
        Charts chart=charts(start,end,interval);
        return new Admin("ADMIN",context.now(),ZONE.getId(),repository.totals(),
                repository.contributions(context,true),repository.moderation(context),repository.activities(context),
                repository.audits(context),List.of(new Shortcut("SYSTEM_CONFIGURATION","System configuration","/admin/settings")),chart);
    }

    Charts charts(Instant from,Instant to,String interval) {
        if (from==null || to==null || !from.isBefore(to) || Duration.between(from,to).compareTo(Duration.ofDays(366))>0) {
            throw invalid("Chart range must satisfy from < to and span at most 366 days");
        }
        if (!Set.of("day","week","month").contains(interval)) {
            throw invalid("interval must be day, week or month");
        }
        ZonedDateTime cursor=floor(from.atZone(ZONE),interval);
        List<Instant> boundaries=new ArrayList<>();
        List<long[]> counts=new ArrayList<>();
        while (cursor.toInstant().isBefore(to)) {
            boundaries.add(cursor.toInstant().isBefore(from) ? from : cursor.toInstant());
            counts.add(new long[6]);
            cursor=advance(cursor,interval);
        }
        boundaries.add(to);
        for (var event:repository.chartEvents(from,to)) {
            int position=Collections.binarySearch(boundaries,event.occurredAt());
            if (position<0) position=-position-2;
            if (position<0 || position>=counts.size()) continue;
            int metric=switch (event.metric()) {
                case "STUDENT" -> 0; case "TEACHER" -> 1; case "CLASSROOM" -> 2;
                case "COURSE" -> 3; case "PROBLEM" -> 4; case "SUBMISSION" -> 5;
                default -> throw new IllegalStateException("Unknown dashboard metric");
            };
            counts.get(position)[metric]+=event.count();
        }
        List<Bucket> buckets=new ArrayList<>();
        for (int i=0;i<counts.size();i++) {
            long[] n=counts.get(i);
            buckets.add(new Bucket(boundaries.get(i),boundaries.get(i+1),n[0],n[1],n[2],n[3],n[4],n[5]));
        }
        return new Charts(from,to,interval,ZONE.getId(),List.copyOf(buckets));
    }

    private DashboardRepository.Context context(UUID account,int size,int days) {
        if (size<1 || size>20 || days<1 || days>30) throw invalid("previewSize must be 1..20 and upcomingDays 1..30");
        Instant now=clock.instant();
        return new DashboardRepository.Context(account,now,now.plus(Duration.ofDays(days)),size);
    }
    private static ZonedDateTime floor(ZonedDateTime time,String interval) {
        LocalDate date=time.toLocalDate();
        if (interval.equals("week")) date=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (interval.equals("month")) date=date.withDayOfMonth(1);
        return date.atStartOfDay(ZONE);
    }
    private static ZonedDateTime advance(ZonedDateTime time,String interval) {
        return switch(interval) { case "week" -> time.plusWeeks(1); case "month" -> time.plusMonths(1); default -> time.plusDays(1); };
    }
    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED",message);
    }
}
