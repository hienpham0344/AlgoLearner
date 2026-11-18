import type { IncomingMessage, ServerResponse } from 'node:http';
import type { Plugin } from 'vite';
import type { Lesson, LessonActivity, LessonCapabilities, LessonInput, LessonProgress } from '../src/features/classroom/lessons/types';
import { validateAttachment, validateLesson } from '../src/features/classroom/lessons/lessonRules';

const TEACHER = 'backend-mock-teacher';
const STUDENT = 'backend-mock-student';
const CLASS_ID = 'demo-class-1';
const FILE_POLICY: LessonCapabilities = { uploadsAvailable: true, activityLinkingAvailable: true, maxFileSizeBytes: 10 * 1024 * 1024, allowedExtensions: ['pdf', 'docx', 'pptx', 'zip', 'png', 'jpg', 'jpeg', 'gif', 'webp'] };
const timestamp = () => new Date().toISOString();
const page = <T,>(items: T[], index = 0) => ({ items: items.slice(index * 20, index * 20 + 20), pagination: {
  page: index, size: 20, totalElements: items.length, totalPages: Math.ceil(items.length / 20), isFirst: index === 0, isLast: (index + 1) * 20 >= items.length,
} });

function respond(response: ServerResponse, status: number, data: unknown, message = 'Lesson demo') {
  response.statusCode = status;
  response.setHeader('Content-Type', 'application/json; charset=utf-8');
  response.setHeader('Cache-Control', 'no-store');
  response.end(JSON.stringify(status < 400 ? { success: true, data, message, timestamp: timestamp() }
    : { success: false, errorCode: `DEMO_${status}`, message, timestamp: timestamp() }));
}
async function body(request: IncomingMessage, limit: number) {
  const chunks: Buffer[] = []; let size = 0;
  for await (const chunk of request) { const bytes = Buffer.from(chunk); size += bytes.length; if (size > limit) throw new Error('Request is too large.'); chunks.push(bytes); }
  return Buffer.concat(chunks);
}

/** In-memory development fixtures. Registered ONLY by `vite --mode lessons-demo`. */
export function lessonsDemoPlugin(): Plugin {
  const now = timestamp();
  const activities: LessonActivity[] = [
    { id: 'demo-quiz-recursion', type: 'QUIZ', title: 'Recursion check-in', linkedLessonId: 'demo-lesson-conditions', available: true, path: null, completed: false },
    { id: 'demo-assignment-arrays', type: 'ASSIGNMENT', title: 'Working with arrays', linkedLessonId: null, available: true, path: null, completed: false },
    { id: 'demo-practice-search', type: 'PRACTICE', title: 'Binary search practice', linkedLessonId: null, available: true, path: null, completed: false },
  ];
  const seed = (id: string, title: string, status: Lesson['status'], classroomId = CLASS_ID): Lesson => ({
    id, classroomId, title, status, previousStatus: status === 'ARCHIVED' ? 'PUBLISHED' : null,
    content: '## Start with a smaller problem\nA recursive function solves a problem by reducing it to a smaller version of itself.\n\n1. Identify a **base case**.\n2. Make progress toward it.\n3. Combine the smaller results.\n\n> Trace a small input by hand before writing the general case.',
    codeExample: { language: 'python', code: 'def factorial(n):\n    if n <= 1:\n        return 1\n    return n * factorial(n - 1)' },
    externalLinks: [{ label: 'Python tutorial: functions', url: 'https://docs.python.org/3/tutorial/controlflow.html#defining-functions' }],
    completionMode: 'MANUAL', requiredActivityIds: [], activities: [], attachments: [], progress: null,
    canDelete: true, createdAt: now, updatedAt: now,
  });
  const lessons = new Map<string, Lesson>([
    ['demo-lesson-recursion', seed('demo-lesson-recursion', 'Understanding recursion', 'PUBLISHED', 'demo-class-1')],
    ['demo-lesson-draft', seed('demo-lesson-draft', 'Planning the next lesson', 'DRAFT', 'demo-class-1')],
    ['demo-lesson-archive', seed('demo-lesson-archive', 'Last term: recursive patterns', 'ARCHIVED', 'demo-class-1')],
    ['demo-lesson-conditions', { ...seed('demo-lesson-conditions', 'Recursion: check your understanding', 'PUBLISHED', 'demo-class-1'), completionMode: 'CONDITION_BASED', requiredActivityIds: ['demo-quiz-recursion'], activities: [activities[0]] }],
    ['demo-lesson-py-lists', seed('demo-lesson-py-lists', 'Lists, tuples, and reference semantics', 'PUBLISHED', 'demo-class-2')],
    ['demo-lesson-algo-graph', seed('demo-lesson-algo-graph', 'Graph traversal: BFS and DFS', 'PUBLISHED', 'cls-algo-2026')],
  ]);
  const progress = new Map<string, LessonProgress>();
  const files = new Map<string, Buffer>();
  const classrooms = ['Algorithms & Data Structures', 'Programming Fundamentals'].map((name, index) => ({
    id: `demo-class-${index + 1}`, name, description: 'Lesson UI demo · sample data, reset when the dev server restarts.', status: 'ACTIVE', ownerId: TEACHER,
    classCode: `DEMO000${index + 1}`, codeExpiresAt: null, codeLocked: false, joiningOpen: true, joinMode: 'AUTO_JOIN', createdAt: now, updatedAt: now,
  }));
  const extraClassrooms = [
    { id: 'cls-algo-2026', name: 'Data Structures & Advanced Algorithms', description: 'Classroom workspace & lessons.', status: 'ACTIVE', ownerId: TEACHER, classCode: 'ALGO2026', codeExpiresAt: null, codeLocked: false, joiningOpen: true, joinMode: 'AUTO_JOIN', createdAt: now, updatedAt: now },
    { id: 'cls-cp-lab', name: 'Competitive Programming Laboratory', description: 'Classroom workspace & lessons.', status: 'ACTIVE', ownerId: TEACHER, classCode: 'CPLAB26X', codeExpiresAt: null, codeLocked: false, joiningOpen: true, joinMode: 'REQUIRE_APPROVAL', createdAt: now, updatedAt: now },
    { id: 'cls-web-eng', name: 'Full-Stack Software Engineering', description: 'Classroom workspace & lessons.', status: 'ACTIVE', ownerId: TEACHER, classCode: 'FULLSTK1', codeExpiresAt: null, codeLocked: false, joiningOpen: true, joinMode: 'AUTO_JOIN', createdAt: now, updatedAt: now },
    { id: 'cls-locked-code', name: 'Operating Systems & Concurrency', description: 'Classroom workspace & lessons.', status: 'ACTIVE', ownerId: TEACHER, classCode: 'OSKERN99', codeExpiresAt: null, codeLocked: true, joiningOpen: true, joinMode: 'AUTO_JOIN', createdAt: now, updatedAt: now },
    { id: 'cls-expired-code', name: 'Foundations of Computer Vision', description: 'Classroom workspace & lessons.', status: 'ACTIVE', ownerId: TEACHER, classCode: 'CVIS2025', codeExpiresAt: null, codeLocked: false, joiningOpen: true, joinMode: 'AUTO_JOIN', createdAt: now, updatedAt: now },
    { id: 'cls-archived-py', name: 'Introduction to Python & Scripting (Spring 2025)', description: 'Archived classroom workspace.', status: 'ARCHIVED', ownerId: TEACHER, classCode: 'PYTH2025', codeExpiresAt: null, codeLocked: false, joiningOpen: false, joinMode: 'AUTO_JOIN', createdAt: now, updatedAt: now },
  ];
  return {
    name: 'algolearner-lessons-demo', apply: 'serve',
    configureServer(server) {
      server.middlewares.use(async (request, response, next) => {
        const url = new URL(request.url || '/', 'http://localhost'); const path = url.pathname;
        if (!path.startsWith('/api/v1/classrooms') && !['/api/v1/auth/me', '/api/v1/auth/logout', '/api/v1/dashboards/student'].includes(path)) return next();
        const token = request.headers.authorization;
        const role = token === 'Bearer mock-teacher' ? 'TEACHER' : token === 'Bearer mock-student' ? 'STUDENT' : null;
        if (!role) return respond(response, token ? 403 : 401, null, 'Sign in with teacher or student to use the lesson demo.');
        const isTeacher = role === 'TEACHER';
        const user = { id: isTeacher ? TEACHER : STUDENT, username: role.toLowerCase(), email: `${role.toLowerCase()}@example.test`, role, fullName: isTeacher ? 'Demo Teacher' : 'Demo Student', lifecycleStatus: 'ACTIVE' };
        if (path === '/api/v1/auth/me') return respond(response, 200, user);
        if (path === '/api/v1/auth/logout') return respond(response, 200, null);
        if (path === '/api/v1/dashboards/student') {
          const preview = <T,>(items: T[]) => ({ items, totalElements: items.length, hasMore: false });
          return respond(response, isTeacher ? 403 : 200, { role, generatedAt: timestamp(), displayTimeZone: 'Asia/Ho_Chi_Minh', classrooms: preview(classrooms.map(item => ({ classroomId: item.id, name: item.name }))),
            upcomingAssignments: preview([]), upcomingQuizzes: preview([]), enrolledCourses: preview([]), publicPracticeStatistics: { solved: 0 }, streak: { currentStreak: 0, highestStreak: 0 }, recentNotifications: preview([]) });
        }
        const parts = path.replace('/api/v1/classrooms', '').split('/').filter(Boolean);
        const classroom = [...classrooms, ...extraClassrooms].find(item => item.id === parts[0]);
        const pageIndex = Math.max(0, Number(url.searchParams.get('page')) || 0);
        const method = request.method || 'GET';
        if (parts.length === 0 && method === 'GET') return respond(response, 200, page(classrooms, pageIndex));
        if (!classroom) return respond(response, 404, null, 'This classroom is not part of the lesson demo.');
        if (parts.length === 1 && method === 'GET') return respond(response, 200, classroom);
        if (parts[1] === 'members' && method === 'GET') return respond(response, 200, page([{ studentId: STUDENT, username: 'student', email: 'student@example.test', status: 'ACTIVE', joinedAt: now }]));
        if (parts[1] === 'join-requests' && method === 'GET') return respond(response, 200, page([]));
        if (parts[1] === 'lesson-capabilities') return respond(response, isTeacher ? 200 : 403, FILE_POLICY);
        if (parts[1] === 'lesson-activities') return respond(response, isTeacher ? 200 : 403, activities.map(activity => ({ ...activity, linkedLessonId: activity.type === 'QUIZ' ? [...lessons.values()].find(lesson => lesson.activities.some(item => item.id === activity.id))?.id || null : null })));
        if (parts[1] !== 'lessons') return respond(response, 405, null, 'This demo supports lesson flows only.');
        const lesson = lessons.get(parts[2]);
        const view = (item: Lesson) => ({ ...item, progress: isTeacher ? null : progress.get(item.id) || null, canDelete: !progress.has(item.id) && item.activities.length === 0 });
        if (parts.length === 2 && method === 'GET') return respond(response, 200, page([...lessons.values()].filter(item => item.classroomId === classroom.id && (isTeacher || item.status === 'PUBLISHED')).map(view), pageIndex));
        if (lesson && (lesson.classroomId !== classroom.id || (!isTeacher && lesson.status !== 'PUBLISHED'))) return respond(response, 403, null, 'This lesson is not available to this account.');
        try {
          if ((parts.length === 2 && method === 'POST') || (parts.length === 3 && method === 'PUT')) {
            if (!isTeacher) return respond(response, 403, null, 'Only the teacher can edit lessons.');
            if (parts.length === 3 && (!lesson || lesson.status === 'ARCHIVED')) return respond(response, 409, null, 'Restore this lesson before editing.');
            const payload = JSON.parse((await body(request, 400_000)).toString()) as LessonInput;
            const input: LessonInput = { title: payload.title, content: payload.content, codeExample: payload.codeExample, externalLinks: payload.externalLinks, completionMode: payload.completionMode, activityIds: payload.activityIds, requiredActivityIds: payload.requiredActivityIds };
            const errors = validateLesson(input, activities.map(item => ({ ...item, linkedLessonId: item.type === 'QUIZ' ? [...lessons.values()].find(value => value.activities.some(activity => activity.id === item.id))?.id || null : null })), lesson?.id);
            if (Object.keys(errors).length) return respond(response, 422, null, Object.values(errors).join(' '));
            const saved: Lesson = { ...(lesson || seed(crypto.randomUUID(), input.title, 'DRAFT')), ...input, classroomId: classroom.id,
              activities: activities.filter(item => input.activityIds.includes(item.id)), updatedAt: timestamp() };
            lessons.set(saved.id, saved); return respond(response, lesson ? 200 : 201, view(saved));
          }
          if (!lesson) return respond(response, 404, null, 'Lesson not found.');
          if (parts.length === 3 && method === 'GET') return respond(response, 200, view(lesson));
          if (parts[3] === 'progress' && method === 'GET') return respond(response, isTeacher ? 200 : 403, page([{ studentId: STUDENT, displayName: 'Demo Student', ...(progress.get(lesson.id) || { completed: false, completedAt: null }) }]));
          if (parts[3] === 'completion' && method === 'POST') {
            if (isTeacher || lesson.completionMode !== 'MANUAL') return respond(response, 403, null, 'Only students can mark a manual lesson complete.');
            progress.set(lesson.id, { completed: true, completedAt: timestamp() }); return respond(response, 200, view(lesson));
          }
          if (parts[3] === 'attachments') {
            if (method === 'GET' && parts[5] === 'download') {
              const file = lesson.attachments.find(item => item.id === parts[4]); const bytes = files.get(parts[4]);
              if (!file || !bytes) return respond(response, 404, null, 'Demo file not found.');
              response.statusCode = 200; response.setHeader('Content-Type', file.contentType); response.end(bytes); return;
            }
            if (!isTeacher || lesson.status === 'ARCHIVED') return respond(response, 403, null, 'This lesson is read-only.');
            if (method === 'DELETE') { lesson.attachments = lesson.attachments.filter(item => item.id !== parts[4]); files.delete(parts[4]); return respond(response, 200, null); }
            if (method === 'POST') {
              const bytes = await body(request, 10 * 1024 * 1024 + 16_384);
              const split = bytes.indexOf('\r\n\r\n'); const headers = bytes.subarray(0, split).toString();
              const name = headers.match(/filename="([^"\r\n]+)"/)?.[1];
              const end = bytes.lastIndexOf('\r\n--');
              if (!name || split < 0 || end <= split + 4) return respond(response, 400, null, 'Select a file to upload.');
              const contents = bytes.subarray(split + 4, end);
              const file = { id: crypto.randomUUID(), name, size: contents.length, contentType: headers.match(/Content-Type: ([^\r\n]+)/i)?.[1] || 'application/octet-stream' };
              const error = validateAttachment(file, FILE_POLICY);
              if (error) return respond(response, 422, null, error);
              lesson.attachments.push(file); files.set(file.id, contents); return respond(response, 201, file);
            }
          }
          if (!isTeacher) return respond(response, 403, null, 'Only the teacher can change lesson state.');
          if (parts.length === 3 && method === 'DELETE') {
            if (!view(lesson).canDelete) return respond(response, 409, null, 'This lesson has learning data. Archive it instead.');
            lessons.delete(lesson.id); return respond(response, 200, null);
          }
          if (method === 'POST') {
            if (parts[3] === 'archive' && lesson.status !== 'ARCHIVED') { lesson.previousStatus = lesson.status; lesson.status = 'ARCHIVED'; }
            else if (parts[3] === 'restore' && lesson.status === 'ARCHIVED') lesson.status = lesson.previousStatus || 'DRAFT';
            else if (parts[3] === 'publish' && lesson.status === 'DRAFT') lesson.status = 'PUBLISHED';
            else if (parts[3] === 'draft' && lesson.status === 'PUBLISHED') lesson.status = 'DRAFT';
            else return respond(response, 409, null, 'This state transition is not available.');
            lesson.updatedAt = timestamp(); return respond(response, 200, view(lesson));
          }
          respond(response, 405, null, 'This demo action is not available.');
        } catch { respond(response, 400, null, 'The demo could not read this request. Your saved lesson is preserved.'); }
      });
    },
  };
}
