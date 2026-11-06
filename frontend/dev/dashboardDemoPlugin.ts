import type { ServerResponse } from 'node:http';
import type { Plugin } from 'vite';

const iso = (offsetHours: number) => new Date(Date.now() + offsetHours * 60 * 60 * 1000).toISOString();
const preview = <T>(items: T[]) => ({ items, totalElements: items.length, hasMore: false });

function respond(response: ServerResponse, status: number, data: unknown) {
  response.statusCode = status;
  response.setHeader('Content-Type', 'application/json; charset=utf-8');
  response.setHeader('Cache-Control', 'no-store');
  response.end(
    JSON.stringify(
      status === 200
        ? { success: true, data, timestamp: new Date().toISOString() }
        : {
            success: false,
            errorCode: status === 401 ? 'UNAUTHORIZED' : 'FORBIDDEN',
            message: 'This dashboard is unavailable to this account.',
            timestamp: new Date().toISOString(),
          },
    ),
  );
}

export function dashboardDemoPlugin(): Plugin {
  return {
    name: 'algolearn-dashboard-demo',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        const route = request.url?.split('?')[0];
        if (
          request.method !== 'GET'
          || !['/api/v1/dashboards/teacher', '/api/v1/admin/dashboard'].includes(route ?? '')
        ) {
          next();
          return;
        }

        const token = request.headers.authorization;

        if (route === '/api/v1/dashboards/teacher') {
          if (token !== 'Bearer mock-teacher') {
            respond(response, token ? 403 : 401, null);
            return;
          }

          const data = {
            role: 'TEACHER',
            generatedAt: iso(0),
            displayTimeZone: 'Asia/Bangkok',
            classrooms: preview([
              { classroomId: 'demo-class-1', name: 'Algorithms & Data Structures' },
              { classroomId: 'demo-class-2', name: 'Programming Fundamentals' },
            ]),
            openOrUpcomingAssignments: preview([
              {
                assignmentId: 'demo-assignment-1',
                classroomId: 'demo-class-1',
                classroomName: 'Algorithms & Data Structures',
                title: 'Build a small sorting utility',
                opensAt: iso(-48),
                effectiveDueAt: iso(5),
              },
              {
                assignmentId: 'demo-assignment-2',
                classroomId: 'demo-class-2',
                classroomName: 'Programming Fundamentals',
                title: 'Control flow & function decomposition',
                opensAt: iso(-24),
                effectiveDueAt: iso(48),
              },
            ]),
            ungradedSubmissions: preview([
              {
                submissionId: 'demo-submission-1',
                kind: 'ASSIGNMENT_WORK',
                classroomId: 'demo-class-1',
                assignmentId: 'demo-assignment-1',
                classPracticeProblemId: null,
                studentAccountId: 'demo-student-1',
                studentUsername: 'learner',
                submittedAt: iso(-2),
              },
              {
                submissionId: 'demo-submission-2',
                kind: 'ASSIGNMENT_WORK',
                classroomId: 'demo-class-2',
                assignmentId: 'demo-assignment-2',
                classPracticeProblemId: null,
                studentAccountId: 'demo-student-2',
                studentUsername: 'minhtran',
                submittedAt: iso(-6),
              },
            ]),
            recentSubmissions: preview([
              {
                submissionId: 'demo-submission-1',
                kind: 'ASSIGNMENT_WORK',
                classroomId: 'demo-class-1',
                assignmentId: 'demo-assignment-1',
                classPracticeProblemId: null,
                studentAccountId: 'demo-student-1',
                studentUsername: 'learner',
                submittedAt: iso(-2),
              },
              {
                submissionId: 'demo-submission-3',
                kind: 'CODE_JUDGE',
                classroomId: 'demo-class-1',
                assignmentId: null,
                classPracticeProblemId: 'demo-problem-1',
                studentAccountId: 'demo-student-3',
                studentUsername: 'anhnguyen',
                submittedAt: iso(-4),
              },
              {
                submissionId: 'demo-submission-2',
                kind: 'ASSIGNMENT_WORK',
                classroomId: 'demo-class-2',
                assignmentId: 'demo-assignment-2',
                classPracticeProblemId: null,
                studentAccountId: 'demo-student-2',
                studentUsername: 'minhtran',
                submittedAt: iso(-6),
              },
            ]),
            recentNotifications: preview([
              {
                notificationId: 'demo-teacher-note-1',
                title: 'New assignment submission from @learner',
                createdAt: iso(-2),
                readAt: null,
              },
              {
                notificationId: 'demo-teacher-note-2',
                title: 'Course module draft approved by moderation',
                createdAt: iso(-18),
                readAt: iso(-12),
              },
              {
                notificationId: 'demo-teacher-note-3',
                title: 'Roster updated for Algorithms & Data Structures',
                createdAt: iso(-36),
                readAt: iso(-30),
              },
            ]),
            pendingContributions: { courseCount: 1, problemCount: 2 },
            classroomStatistics: preview([
              {
                classroomId: 'demo-class-1',
                name: 'Algorithms & Data Structures',
                activeStudentCount: 24,
                publishedAssignmentCount: 3,
                publishedQuizCount: 2,
                ungradedWorkCount: 4,
              },
              {
                classroomId: 'demo-class-2',
                name: 'Programming Fundamentals',
                activeStudentCount: 18,
                publishedAssignmentCount: 2,
                publishedQuizCount: 1,
                ungradedWorkCount: 1,
              },
            ]),
          };

          respond(response, 200, data);
          return;
        }

        if (token !== 'Bearer mock-admin') {
          respond(response, token ? 403 : 401, null);
          return;
        }

        const dailyCounts = [14, 19, 16, 22, 28, 21, 25, 31, 24, 29, 36, 33, 41, 38];
        const chartItems = dailyCounts.map((count, index) => {
          const daysAgo = dailyCounts.length - 1 - index;
          return {
            startsAt: iso(-(daysAgo + 1) * 24),
            endsAt: iso(-daysAgo * 24),
            studentCreatedCount: (index % 4) + 1,
            teacherCreatedCount: index % 5 === 0 ? 1 : 0,
            classroomCreatedCount: index % 3 === 0 ? 1 : 0,
            publicCourseCreatedCount: 0,
            publicProblemCreatedCount: index % 2,
            officialSubmissionCount: count,
          };
        });

        const data = {
          role: 'ADMIN',
          generatedAt: iso(0),
          displayTimeZone: 'Asia/Bangkok',
          totals: {
            studentCount: 128,
            teacherCount: 9,
            classroomCount: 16,
            publicCourseCount: 5,
            publicProblemCount: 64,
            officialSubmissionCount: 387,
          },
          pendingTeacherContributions: { courseCount: 2, problemCount: 3 },
          accountModeration: {
            lockedAccountCount: 3,
            suspendedAccountCount: 1,
            confirmedViolationAccountCount: 0,
          },
          recentActivities: preview([
            {
              eventId: 'demo-activity-1',
              sourceModule: 'classroom',
              action: 'CLASS_CREATED',
              summary: 'Classroom ALGO-2026 opened for enrollment',
              occurredAt: iso(-2),
            },
            {
              eventId: 'demo-activity-2',
              sourceModule: 'catalog',
              action: 'PROBLEM_PUBLISHED',
              summary: 'Published practice problem: Merge Intervals',
              occurredAt: iso(-9),
            },
            {
              eventId: 'demo-activity-3',
              sourceModule: 'evaluation',
              action: 'BATCH_GRADED',
              summary: 'Completed automated judge run for 24 submissions',
              occurredAt: iso(-21),
            },
          ]),
          importantAuditEvents: preview([
            {
              auditId: 'demo-audit-1',
              action: 'ACCOUNT_LOCKED',
              outcome: 'SUCCESS',
              occurredAt: iso(-5),
            },
            {
              auditId: 'demo-audit-2',
              action: 'SYSTEM_CONFIGURATION_UPDATED',
              outcome: 'SUCCESS',
              occurredAt: iso(-28),
            },
          ]),
          configurationShortcuts: [
            { code: 'JUDGE_SANDBOX', label: 'Execution sandbox limits', path: '/admin/settings/judge' },
            { code: 'AUTH_SECURITY', label: 'Authentication & lockout policy', path: '/admin/settings/auth' },
          ],
          charts: {
            from: chartItems[0].startsAt,
            to: chartItems[chartItems.length - 1].endsAt,
            interval: 'day',
            timeZone: 'Asia/Bangkok',
            items: chartItems,
          },
        };

        respond(response, 200, data);
      });
    },
  };
}
