import test from 'node:test';
import assert from 'node:assert/strict';
import { mapStudentDashboard } from '../src/features/dashboard/mapStudentDashboard.ts';

test('student dashboard maps only account-scoped API items and preserves UTC deadlines', () => {
  const source = {
    classrooms: { items: [{ classroomId: 'class-1', name: 'Algorithms' }], totalElements: 1, hasMore: false },
    upcomingAssignments: { items: [{ assignmentId: 'assignment-1', classroomId: 'class-1', classroomName: 'Algorithms', title: 'Sorting', effectiveDueAt: '2026-11-06T01:00:00Z' }], totalElements: 1, hasMore: false },
    upcomingQuizzes: { items: [], totalElements: 0, hasMore: false },
    enrolledCourses: { items: [{ courseId: 'course-1', title: 'Python', completedUnits: 3, totalUnits: 5, progressPercent: 60 }], totalElements: 1, hasMore: false },
    publicPracticeStatistics: { solved: 7 }, streak: { currentStreak: 2, highestStreak: 4 },
    recentNotifications: { items: [{ notificationId: 'note-1', title: 'New assignment', createdAt: '2026-11-05T08:00:00Z', readAt: null }], totalElements: 1, hasMore: false },
  };
  const result = mapStudentDashboard(source);
  assert.equal(result.courses[0].progressPercent, 60);
  assert.equal(result.courses[0].href, undefined);
  assert.equal(result.deadlines[0].dueAt, '2026-11-06T01:00:00Z');
  assert.equal(result.deadlines[0].status, undefined);
  assert.equal(result.classes[0].href, undefined);
  assert.equal(result.practice.totalSolved, 7);
  assert.equal(result.practice.currentStreak, 2);
  assert.equal(result.practice.easy, null);
  assert.equal(result.notifications[0].read, false);
});

test('empty API previews stay empty and zero practice values stay zero', () => {
  const empty = { items: [], totalElements: 0, hasMore: false };
  const result = mapStudentDashboard({ classrooms: empty, upcomingAssignments: empty, upcomingQuizzes: empty, enrolledCourses: empty, recentNotifications: empty, publicPracticeStatistics: { solved: 0 }, streak: { currentStreak: 0, highestStreak: 0 } });
  assert.deepEqual(result.courses, []);
  assert.deepEqual(result.deadlines, []);
  assert.deepEqual(result.classes, []);
  assert.deepEqual(result.notifications, []);
  assert.equal(result.practice.totalSolved, 0);
  assert.equal(result.practice.currentStreak, 0);
});
