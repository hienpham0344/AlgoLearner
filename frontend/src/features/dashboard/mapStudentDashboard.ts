import type { StudentDashboardResponse } from './api/dashboardApi';
import type { StudentDashboardData } from './types';

export function mapStudentDashboard(source: StudentDashboardResponse): StudentDashboardData {
  return {
    courses: source.enrolledCourses.items.map(course => ({
      id: course.courseId, title: course.title, topic: 'PUBLIC COURSE', progressPercent: course.progressPercent,
    })),
    deadlines: [
      ...source.upcomingAssignments.items.map(item => ({ id: item.assignmentId, title: item.title, className: item.classroomName, kind: 'Assignment' as const, dueAt: item.effectiveDueAt })),
      ...source.upcomingQuizzes.items.map(item => ({ id: item.quizId, title: item.title, className: item.classroomName, kind: 'Quiz' as const, dueAt: item.effectiveClosesAt })),
    ],
    classes: source.classrooms.items.map(item => ({ id: item.classroomId, name: item.name, code: '' })),
    practice: { currentStreak: source.streak.currentStreak, totalSolved: source.publicPracticeStatistics.solved, easy: null, medium: null, hard: null },
    notifications: source.recentNotifications.items.map(item => ({ id: item.notificationId, title: item.title, detail: '', createdAt: item.createdAt, read: item.readAt !== null })),
  };
}
