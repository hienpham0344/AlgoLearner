import { apiClient } from '@/services/apiClient';
import type { ApiResponse } from '@/types/api';

export interface Preview<T> { items: T[]; totalElements: number; hasMore: boolean }
export interface Classroom { classroomId: string; name: string }
export interface Assignment { assignmentId: string; classroomId: string; classroomName: string; title: string; opensAt: string; effectiveDueAt: string }
export interface Quiz { quizId: string; classroomId: string; classroomName: string; title: string; opensAt: string; effectiveClosesAt: string }
export interface Course { courseId: string; title: string; completedUnits: number; totalUnits: number; progressPercent: number }
export interface Notification { notificationId: string; title: string; createdAt: string; readAt: string | null }
export interface StudentDashboardResponse {
  role: 'STUDENT'; generatedAt: string; displayTimeZone: string;
  classrooms: Preview<Classroom>; upcomingAssignments: Preview<Assignment>; upcomingQuizzes: Preview<Quiz>;
  enrolledCourses: Preview<Course>; publicPracticeStatistics: { solved: number };
  streak: { currentStreak: number; highestStreak: number }; recentNotifications: Preview<Notification>;
}
export interface Submission { submissionId: string; kind: string; classroomId: string; assignmentId: string | null; classPracticeProblemId: string | null; studentAccountId: string; studentUsername: string; submittedAt: string }
export interface ClassStatistics { classroomId: string; name: string; activeStudentCount: number; publishedAssignmentCount: number; publishedQuizCount: number; ungradedWorkCount: number }
export interface TeacherDashboardResponse {
  role: 'TEACHER'; generatedAt: string; displayTimeZone: string;
  classrooms: Preview<Classroom>; openOrUpcomingAssignments: Preview<Assignment>;
  ungradedSubmissions: Preview<Submission>; recentSubmissions: Preview<Submission>;
  recentNotifications: Preview<Notification>; pendingContributions: { courseCount: number; problemCount: number };
  classroomStatistics: Preview<ClassStatistics>;
}
export interface AdminActivity { eventId: string; sourceModule: string; action: string; summary: string; occurredAt: string }
export interface AdminAudit { auditId: string; action: string; outcome: string; occurredAt: string }
export interface AdminDashboardResponse {
  role: 'ADMIN'; generatedAt: string; displayTimeZone: string;
  totals: { studentCount: number; teacherCount: number; classroomCount: number; publicCourseCount: number; publicProblemCount: number; officialSubmissionCount: number };
  pendingTeacherContributions: { courseCount: number; problemCount: number };
  accountModeration: { lockedAccountCount: number; suspendedAccountCount: number; confirmedViolationAccountCount: number };
  recentActivities: Preview<AdminActivity>; importantAuditEvents: Preview<AdminAudit>;
  configurationShortcuts: { code: string; label: string; path: string }[];
  charts: { from: string; to: string; interval: string; timeZone: string; items: { startsAt: string; endsAt: string; studentCreatedCount: number; teacherCreatedCount: number; classroomCreatedCount: number; publicCourseCreatedCount: number; publicProblemCreatedCount: number; officialSubmissionCount: number }[] };
}

export const dashboardKeys = {
  all: ['dashboard'] as const,
  student: () => [...dashboardKeys.all, 'student'] as const,
  teacher: () => [...dashboardKeys.all, 'teacher'] as const,
  admin: () => [...dashboardKeys.all, 'admin'] as const,
};
export const profileKeys = {
  all: ['profile'] as const,
  own: () => [...profileKeys.all, 'own'] as const,
  public: (username: string) => [...profileKeys.all, 'public', username] as const,
};
export const dashboardApi = {
  student: async (signal?: AbortSignal) => (await apiClient.get<ApiResponse<StudentDashboardResponse>>('/dashboards/student', { signal })).data.data,
  teacher: async (signal?: AbortSignal) => (await apiClient.get<ApiResponse<TeacherDashboardResponse>>('/dashboards/teacher', { signal })).data.data,
  admin: async (signal?: AbortSignal) => (await apiClient.get<ApiResponse<AdminDashboardResponse>>('/admin/dashboard', { signal })).data.data,
};
