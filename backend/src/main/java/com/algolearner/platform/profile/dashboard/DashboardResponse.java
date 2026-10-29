package com.algolearner.platform.profile.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models only; no persistence entities or private student grades are exposed. */
public final class DashboardResponse {
    private DashboardResponse() { }

    public record Preview<T>(List<T> items, long totalElements, boolean hasMore) { }
    public record Classroom(UUID classroomId, String name) { }
    public record Assignment(UUID assignmentId, UUID classroomId, String classroomName,
                             String title, Instant opensAt, Instant effectiveDueAt) { }
    public record Quiz(UUID quizId, UUID classroomId, String classroomName,
                       String title, Instant opensAt, Instant effectiveClosesAt) { }
    public record Course(UUID courseId, String title, int completedUnits, int totalUnits,
                         double progressPercent) { }
    public record Notification(UUID notificationId, String title, Instant createdAt, Instant readAt) { }
    public record Streak(int currentStreak, int highestStreak) { }
    public record Practice(long solved) { }
    public record Contributions(long courseCount, long problemCount) { }
    public record Submission(UUID submissionId, String kind, UUID classroomId,
                             UUID assignmentId, UUID classPracticeProblemId,
                             UUID studentAccountId, String studentUsername, Instant submittedAt) { }
    public record ClassStatistics(UUID classroomId, String name, long activeStudentCount,
                                  long publishedAssignmentCount, long publishedQuizCount,
                                  long ungradedWorkCount) { }
    public record Student(String role, Instant generatedAt, String displayTimeZone,
                          Preview<Classroom> classrooms, Preview<Assignment> upcomingAssignments,
                          Preview<Quiz> upcomingQuizzes, Preview<Course> enrolledCourses,
                          Practice publicPracticeStatistics, Streak streak,
                          Preview<Notification> recentNotifications) { }
    public record Teacher(String role, Instant generatedAt, String displayTimeZone,
                          Preview<Classroom> classrooms, Preview<Assignment> openOrUpcomingAssignments,
                          Preview<Submission> ungradedSubmissions, Preview<Submission> recentSubmissions,
                          Preview<Notification> recentNotifications, Contributions pendingContributions,
                          Preview<ClassStatistics> classroomStatistics) { }
    public record Totals(long studentCount, long teacherCount, long classroomCount,
                         long publicCourseCount, long publicProblemCount, long officialSubmissionCount) { }
    public record Moderation(long lockedAccountCount, long suspendedAccountCount,
                             long confirmedViolationAccountCount) { }
    public record Activity(String eventId, String sourceModule, String action, String summary,
                           Instant occurredAt) { }
    public record Audit(String auditId, String action, String outcome, Instant occurredAt) { }
    public record Shortcut(String code, String label, String path) { }
    public record Bucket(Instant startsAt, Instant endsAt, long studentCreatedCount,
                         long teacherCreatedCount, long classroomCreatedCount,
                         long publicCourseCreatedCount, long publicProblemCreatedCount,
                         long officialSubmissionCount) { }
    public record Charts(Instant from, Instant to, String interval, String timeZone, List<Bucket> items) { }
    public record Admin(String role, Instant generatedAt, String displayTimeZone, Totals totals,
                        Contributions pendingTeacherContributions, Moderation accountModeration,
                        Preview<Activity> recentActivities, Preview<Audit> importantAuditEvents,
                        List<Shortcut> configurationShortcuts, Charts charts) { }
}
