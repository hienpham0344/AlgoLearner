import type { ServerResponse } from 'node:http';
import type { Plugin } from 'vite';

const iso = (offsetHours: number) => new Date(Date.now() + offsetHours * 60 * 60 * 1000).toISOString();
const preview = <T>(items: T[]) => ({ items, totalElements: items.length, hasMore: false });

function respond(response: ServerResponse, status: number, data: unknown) {
  response.statusCode = status;
  response.setHeader('Content-Type', 'application/json; charset=utf-8');
  response.setHeader('Cache-Control', 'no-store');
  response.end(JSON.stringify(status === 200
    ? { success: true, data, timestamp: new Date().toISOString() }
    : { success: false, errorCode: status === 401 ? 'UNAUTHORIZED' : 'FORBIDDEN', message: 'This dashboard is unavailable to this account.', timestamp: new Date().toISOString() }));
}

export function dashboardDemoPlugin(): Plugin {
  return {
    name: 'algolearn-dashboard-demo',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        const route = request.url?.split('?')[0];
        if (request.method !== 'GET' || !['/api/v1/dashboards/teacher', '/api/v1/admin/dashboard'].includes(route ?? '')) { next(); return; }
        const token = request.headers.authorization;
        if (route === '/api/v1/dashboards/teacher') {
          if (token !== 'Bearer mock-teacher') { respond(response, token ? 403 : 401, null); return; }
          const data = {
            role: 'TEACHER', generatedAt: iso(0), displayTimeZone: 'Asia/Bangkok',
            classrooms: preview([{ classroomId: 'demo-class-1', name: 'Algorithms & Data Structures' }, { classroomId: 'demo-class-2', name: 'Programming Fundamentals' }]),
            openOrUpcomingAssignments: preview([{ assignmentId: 'demo-assignment-1', classroomId: 'demo-class-1', classroomName: 'Algorithms & Data Structures', title: 'Build a sorting utility', opensAt: iso(-48), effectiveDueAt: iso(36) }]),
            ungradedSubmissions: preview([{ submissionId: 'demo-submission-1', kind: 'Assignment', classroomId: 'demo-class-1', assignmentId: 'demo-assignment-1', classPracticeProblemId: null, studentAccountId: 'demo-student-1', studentUsername: 'learner', submittedAt: iso(-3) }]),
            recentSubmissions: preview([{ submissionId: 'demo-submission-2', kind: 'Quiz', classroomId: 'demo-class-2', assignmentId: null, classPracticeProblemId: null, studentAccountId: 'demo-student-2', studentUsername: 'student2', submittedAt: iso(-2) }]),
            recentNotifications: preview([{ notificationId: 'demo-note-1', title: 'New work to review', createdAt: iso(-2), readAt: null }]),
            pendingContributions: { courseCount: 1, problemCount: 2 },
            classroomStatistics: preview([{ classroomId: 'demo-class-1', name: 'Algorithms & Data Structures', activeStudentCount: 24, publishedAssignmentCount: 3, publishedQuizCount: 2, ungradedWorkCount: 4 }, { classroomId: 'demo-class-2', name: 'Programming Fundamentals', activeStudentCount: 18, publishedAssignmentCount: 2, publishedQuizCount: 1, ungradedWorkCount: 1 }]),
          };
          respond(response, 200, data);
          return;
        }
        if (token !== 'Bearer mock-admin') { respond(response, token ? 403 : 401, null); return; }
        const chartItems = [5, 8, 6, 11, 9, 14, 12].map((count, index) => ({
          startsAt: iso((index - 6) * 24), endsAt: iso((index - 5) * 24),
          studentCreatedCount: index + 1, teacherCreatedCount: 0, classroomCreatedCount: index % 2,
          publicCourseCreatedCount: 0, publicProblemCreatedCount: index % 3, officialSubmissionCount: count,
        }));
        const data = {
          role: 'ADMIN', generatedAt: iso(0), displayTimeZone: 'Asia/Bangkok',
          totals: { studentCount: 128, teacherCount: 9, classroomCount: 16, publicCourseCount: 5, publicProblemCount: 64, officialSubmissionCount: 312 },
          pendingTeacherContributions: { courseCount: 2, problemCount: 3 },
          accountModeration: { lockedAccountCount: 3, suspendedAccountCount: 1, confirmedViolationAccountCount: 0 },
          recentActivities: preview([{ eventId: 'demo-activity-1', sourceModule: 'classroom', action: 'CLASS_CREATED', summary: 'A new class was created', occurredAt: iso(-2) }]),
          importantAuditEvents: preview([{ auditId: 'demo-audit-1', action: 'ACCOUNT_LOCKED', outcome: 'SUCCESS', occurredAt: iso(-5) }]),
          configurationShortcuts: [],
          charts: { from: chartItems[0].startsAt, to: chartItems[chartItems.length - 1].endsAt, interval: 'day', timeZone: 'Asia/Bangkok', items: chartItems },
        };
        respond(response, 200, data);
      });
    },
  };
}
