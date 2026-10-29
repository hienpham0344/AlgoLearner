package com.algolearner.platform.profile.dashboard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static com.algolearner.platform.profile.dashboard.DashboardResponse.*;
/** Read port: implementations must apply account/owner scope before preview limits. */
public interface DashboardRepository {
    record Context(UUID accountId, Instant now, Instant upcomingEnd, int limit) { }
    record ChartEvent(Instant occurredAt, String metric, long count) { }
    Preview<Classroom> studentClassrooms(Context c);
    Preview<Classroom> teacherClassrooms(Context c);
    Preview<Assignment> studentAssignments(Context c);
    Preview<Assignment> teacherAssignments(Context c);
    Preview<Quiz> studentQuizzes(Context c);
    Preview<Course> courses(Context c);
    Preview<Notification> notifications(Context c);
    Preview<Submission> ungraded(Context c);
    Preview<Submission> recentSubmissions(Context c);
    Preview<ClassStatistics> classStatistics(Context c);
    Contributions contributions(Context c, boolean allTeachers);
    Totals totals();
    Moderation moderation(Context c);
    Preview<Activity> activities(Context c);
    Preview<Audit> audits(Context c);
    List<ChartEvent> chartEvents(Instant from, Instant to);
}
