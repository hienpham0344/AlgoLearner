import type { IncomingMessage, ServerResponse } from 'node:http';
import type { Plugin } from 'vite';
import type {
  CodeExample,
  ExternalLink,
  LessonActivity,
  LessonCapabilities,
  LessonProgress,
  LessonStatus,
} from '../src/features/classroom/lessons/types';
import { validateAttachment, validateLesson } from '../src/features/classroom/lessons/lessonRules';

const TEACHER = 'backend-mock-teacher';
const STUDENT = 'backend-mock-student';
const CLASS_ID = 'demo-class-1';
const FILE_POLICY: LessonCapabilities = {
  uploadsAvailable: true,
  activityLinkingAvailable: true,
  maxFileSizeBytes: 10 * 1024 * 1024,
  allowedExtensions: ['pdf', 'docx', 'pptx', 'zip', 'png', 'jpg', 'jpeg', 'gif', 'webp'],
};
const timestamp = () => new Date().toISOString();
const page = <T,>(items: T[], index = 0) => ({
  items: items.slice(index * 20, index * 20 + 20),
  pagination: {
    page: index,
    size: 20,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / 20),
    isFirst: index === 0,
    isLast: (index + 1) * 20 >= items.length,
  },
});

type DemoMaterialType = 'PDF' | 'DOCX' | 'PPTX' | 'ZIP' | 'IMAGE';

interface DemoMaterial {
  id: string;
  fileName: string;
  fileUrl: string;
  fileType: DemoMaterialType;
  fileSize: number;
  createdAt: string;
}

interface DemoLinkedItem {
  id: string;
  itemType: LessonActivity['type'];
  itemId: string;
  displayOrder: number;
  title: string;
  available: boolean;
}

interface DemoStoredFile {
  id: string;
  name: string;
  mediaType: string;
  kind: DemoMaterialType;
  size: number;
  contents: Buffer;
  createdAt: string;
}

interface DemoLessonRecord {
  id: string;
  classroomId: string;
  title: string;
  content: string;
  codeExample: string;
  externalLinks: string;
  status: LessonStatus;
  previousStatus: Exclude<LessonStatus, 'ARCHIVED'> | null;
  completionMode: 'MANUAL' | 'AUTO_ITEMS' | 'NONE';
  displayOrder: number;
  materials: DemoMaterial[];
  linkedItems: DemoLinkedItem[];
  requiredItemIds: string[];
  ruleVersion: number;
  createdAt: string;
  updatedAt: string;
}

interface DemoLessonPayload {
  title: string;
  content?: string;
  codeExample?: string;
  externalLinks?: string;
  completionMode?: 'MANUAL' | 'AUTO_ITEMS' | 'NONE';
}

interface DemoCompletionRulePayload {
  operator: 'ALL' | 'ANY';
  conditions: Array<{
    itemType: LessonActivity['type'];
    itemId: string;
    requirement: 'SUBMITTED' | 'SCORE_AT_LEAST';
    scorePercent: number | null;
  }>;
}

function materialKind(name: string): DemoMaterialType {
  const ext = name.split('.').pop()?.toLowerCase();
  if (ext === 'docx') return 'DOCX';
  if (ext === 'pptx') return 'PPTX';
  if (ext === 'zip') return 'ZIP';
  if (ext === 'png' || ext === 'jpg' || ext === 'jpeg' || ext === 'gif' || ext === 'webp') return 'IMAGE';
  return 'PDF';
}

function parseCodeExample(raw: string | undefined): CodeExample | null {
  if (!raw?.trim()) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<CodeExample>;
    if (!parsed.code) return null;
    const language = parsed.language === 'java' || parsed.language === 'python' || parsed.language === 'text' ? parsed.language : 'text';
    return { language, code: parsed.code };
  } catch {
    return { language: 'text', code: raw };
  }
}

function parseExternalLinks(raw: string | undefined): ExternalLink[] {
  if (!raw?.trim()) return [];
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed as ExternalLink[] : [];
  } catch {
    return [];
  }
}

function respond(response: ServerResponse, status: number, data: unknown, message = 'Lesson demo') {
  response.statusCode = status;
  response.setHeader('Content-Type', 'application/json; charset=utf-8');
  response.setHeader('Cache-Control', 'no-store');
  response.end(JSON.stringify(status < 400
    ? { success: true, data, message, timestamp: timestamp() }
    : { success: false, errorCode: `DEMO_${status}`, message, timestamp: timestamp() }));
}

function noContent(response: ServerResponse) {
  response.statusCode = 204;
  response.setHeader('Cache-Control', 'no-store');
  response.end();
}

async function body(request: IncomingMessage, limit: number) {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    const bytes = Buffer.from(chunk);
    size += bytes.length;
    if (size > limit) throw new Error('Request is too large.');
    chunks.push(bytes);
  }
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
  const seed = (id: string, title: string, status: LessonStatus, classroomId = CLASS_ID, displayOrder = 0): DemoLessonRecord => ({
    id,
    classroomId,
    title,
    status,
    previousStatus: status === 'ARCHIVED' ? 'PUBLISHED' : null,
    content: '## Start with a smaller problem\nA recursive function solves a problem by reducing it to a smaller version of itself.\n\n1. Identify a **base case**.\n2. Make progress toward it.\n3. Combine the smaller results.\n\n> Trace a small input by hand before writing the general case.',
    codeExample: JSON.stringify({ language: 'python', code: 'def factorial(n):\n    if n <= 1:\n        return 1\n    return n * factorial(n - 1)' }),
    externalLinks: JSON.stringify([{ label: 'Python tutorial: functions', url: 'https://docs.python.org/3/tutorial/controlflow.html#defining-functions' }]),
    completionMode: 'MANUAL',
    displayOrder,
    materials: [],
    linkedItems: [],
    requiredItemIds: [],
    ruleVersion: 0,
    createdAt: now,
    updatedAt: now,
  });
  const lessons = new Map<string, DemoLessonRecord>([
    ['demo-lesson-recursion', seed('demo-lesson-recursion', 'Understanding recursion', 'PUBLISHED', 'demo-class-1', 0)],
    ['demo-lesson-draft', seed('demo-lesson-draft', 'Planning the next lesson', 'DRAFT', 'demo-class-1', 1)],
    ['demo-lesson-archive', seed('demo-lesson-archive', 'Last term: recursive patterns', 'ARCHIVED', 'demo-class-1', 2)],
    ['demo-lesson-conditions', {
      ...seed('demo-lesson-conditions', 'Recursion: check your understanding', 'PUBLISHED', 'demo-class-1', 3),
      completionMode: 'AUTO_ITEMS',
      linkedItems: [{ id: 'demo-link-1', itemType: 'QUIZ', itemId: 'demo-quiz-recursion', displayOrder: 0, title: 'Recursion check-in', available: true }],
      requiredItemIds: ['demo-quiz-recursion'],
      ruleVersion: 1,
    }],
    ['demo-lesson-py-lists', seed('demo-lesson-py-lists', 'Lists, tuples, and reference semantics', 'PUBLISHED', 'demo-class-2', 0)],
    ['demo-lesson-algo-graph', seed('demo-lesson-algo-graph', 'Graph traversal: BFS and DFS', 'PUBLISHED', 'cls-algo-2026', 0)],
  ]);
  const progress = new Map<string, LessonProgress>();
  const files = new Map<string, DemoStoredFile>();
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
        const url = new URL(request.url || '/', 'http://localhost');
        const path = url.pathname;
        if (
          !path.startsWith('/api/v1/classrooms') &&
          !path.startsWith('/api/v1/files/') &&
          !['/api/v1/auth/me', '/api/v1/auth/logout', '/api/v1/dashboards/student'].includes(path)
        ) {
          return next();
        }
        const token = request.headers.authorization;
        const role = token === 'Bearer mock-teacher' ? 'TEACHER' : token === 'Bearer mock-student' ? 'STUDENT' : null;
        if (!role) return respond(response, token ? 403 : 401, null, 'Sign in with teacher or student to use the lesson demo.');
        const isTeacher = role === 'TEACHER';
        const user = { id: isTeacher ? TEACHER : STUDENT, username: role.toLowerCase(), email: `${role.toLowerCase()}@example.test`, role, fullName: isTeacher ? 'Demo Teacher' : 'Demo Student', lifecycleStatus: 'ACTIVE' };
        if (path === '/api/v1/auth/me') return respond(response, 200, user);
        if (path === '/api/v1/auth/logout') return respond(response, 200, null);
        if (path === '/api/v1/dashboards/student') {
          const preview = <T,>(items: T[]) => ({ items, totalElements: items.length, hasMore: false });
          return respond(response, isTeacher ? 403 : 200, {
            role, generatedAt: timestamp(), displayTimeZone: 'Asia/Ho_Chi_Minh',
            classrooms: preview(classrooms.map(item => ({ classroomId: item.id, name: item.name }))),
            upcomingAssignments: preview([]), upcomingQuizzes: preview([]), enrolledCourses: preview([]),
            publicPracticeStatistics: { solved: 0 }, streak: { currentStreak: 0, highestStreak: 0 }, recentNotifications: preview([]),
          });
        }
        const method = request.method || 'GET';
        if (path.startsWith('/api/v1/files/')) {
          const fileParts = path.replace('/api/v1/files/', '').split('/');
          if (method === 'GET' && fileParts.length === 2 && fileParts[1] === 'content') {
            const stored = files.get(fileParts[0]);
            if (!stored) return respond(response, 404, null, 'Demo file not found.');
            response.statusCode = 200;
            response.setHeader('Content-Type', stored.mediaType);
            response.setHeader('Cache-Control', 'no-store');
            response.end(stored.contents);
            return;
          }
          return respond(response, 405, null, 'This demo file action is not available.');
        }

        const parts = path.replace('/api/v1/classrooms', '').split('/').filter(Boolean);
        const classroom = [...classrooms, ...extraClassrooms].find(item => item.id === parts[0]);
        const pageIndex = Math.max(0, Number(url.searchParams.get('page')) || 0);
        if (parts.length === 0 && method === 'GET') return respond(response, 200, page(classrooms, pageIndex));
        if (!classroom) return respond(response, 404, null, 'This classroom is not part of the lesson demo.');
        if (parts.length === 1 && method === 'GET') return respond(response, 200, classroom);
        if (parts[1] === 'members' && method === 'GET') return respond(response, 200, page([{ studentId: STUDENT, username: 'student', email: 'student@example.test', status: 'ACTIVE', joinedAt: now }]));
        if (parts[1] === 'join-requests' && method === 'GET') return respond(response, 200, page([]));

        if (parts[1] === 'files' && parts.length === 2 && method === 'POST') {
          if (!isTeacher) return respond(response, 403, null, 'Only the teacher can upload lesson materials.');
          if (url.searchParams.get('purpose') !== 'LESSON_MATERIAL') {
            return respond(response, 400, null, 'Invalid file upload purpose.');
          }
          try {
            const contentType = request.headers['content-type'];
            if (!contentType?.startsWith('multipart/form-data')) return respond(response, 400, null, 'Select a file to upload.');
            const raw = await body(request, 10 * 1024 * 1024 + 16_384);
            const form = await new Request('http://localhost/upload', { method: 'POST', headers: { 'Content-Type': contentType }, body: raw }).formData();
            const uploaded = form.get('file');
            if (!(uploaded instanceof Blob)) return respond(response, 400, null, 'Select a file to upload.');
            const fileName = uploaded instanceof File && uploaded.name ? uploaded.name : 'attachment.pdf';
            const error = validateAttachment({ name: fileName, size: uploaded.size }, FILE_POLICY);
            if (error) return respond(response, 400, null, error);
            const contents = Buffer.from(await uploaded.arrayBuffer());
            const stored: DemoStoredFile = {
              id: crypto.randomUUID(),
              name: fileName,
              mediaType: uploaded.type || 'application/octet-stream',
              kind: materialKind(fileName),
              size: contents.length,
              contents,
              createdAt: timestamp(),
            };
            files.set(stored.id, stored);
            return respond(response, 201, {
              id: stored.id,
              name: stored.name,
              mediaType: stored.mediaType,
              kind: stored.kind,
              size: stored.size,
              purpose: 'LESSON_MATERIAL',
              state: 'READY',
              sha256: 'demo-sha256',
              createdAt: stored.createdAt,
            });
          } catch {
            return respond(response, 400, null, 'Could not read uploaded file.');
          }
        }

        if (parts[1] !== 'lessons') return respond(response, 405, null, 'This demo supports lesson flows only.');

        if (parts.length === 3 && parts[2] === 'capabilities' && method === 'GET') {
          return respond(response, isTeacher ? 200 : 403, FILE_POLICY);
        }
        if (parts.length === 3 && parts[2] === 'activities' && method === 'GET') {
          return respond(response, isTeacher ? 200 : 403, activities.map(activity => ({
            id: activity.id,
            type: activity.type,
            title: activity.title,
            linkedLessonId: activity.type === 'QUIZ'
              ? [...lessons.values()].find(
                  item => item.classroomId === classroom.id && item.linkedItems.some(link => link.itemId === activity.id),
                )?.id ?? null
              : null,
            available: activity.available,
          })));
        }

        const summaryView = (item: DemoLessonRecord) => {
          const p = isTeacher ? null : progress.get(item.id) || null;
          return {
            id: item.id,
            classroomId: item.classroomId,
            title: item.title,
            status: item.status,
            completionMode: item.completionMode,
            displayOrder: item.displayOrder,
            isCompleted: p ? p.completed : null,
            completedAt: p ? p.completedAt : null,
            createdAt: item.createdAt,
            updatedAt: item.updatedAt,
          };
        };

        const detailView = (item: DemoLessonRecord) => {
          const p = isTeacher ? null : progress.get(item.id) || null;
          const canDelete = isTeacher && item.ruleVersion === 0 && !progress.has(item.id) && item.linkedItems.length === 0;
          return {
            id: item.id,
            classroomId: item.classroomId,
            title: item.title,
            content: item.content,
            codeExample: item.codeExample,
            externalLinks: item.externalLinks,
            status: item.status,
            completionMode: item.completionMode,
            displayOrder: item.displayOrder,
            materials: item.materials,
            linkedItems: isTeacher ? item.linkedItems : item.linkedItems.filter(link => link.available),
            isCompleted: p ? p.completed : null,
            completedAt: p ? p.completedAt : null,
            createdAt: item.createdAt,
            updatedAt: item.updatedAt,
            previousStatus: isTeacher ? item.previousStatus : null,
            canDelete,
            requiredItemIds: item.requiredItemIds,
          };
        };

        if (parts.length === 2 && method === 'GET') {
          return respond(
            response,
            200,
            [...lessons.values()]
              .filter(item => item.classroomId === classroom.id && (isTeacher || item.status === 'PUBLISHED'))
              .map(summaryView),
          );
        }

        const lesson = lessons.get(parts[2]);
        if (lesson && (lesson.classroomId !== classroom.id || (!isTeacher && lesson.status !== 'PUBLISHED'))) {
          return respond(response, 403, null, 'This lesson is not available to this account.');
        }

        try {
          if ((parts.length === 2 && method === 'POST') || (parts.length === 3 && method === 'PUT')) {
            if (!isTeacher) return respond(response, 403, null, 'Only the teacher can edit lessons.');
            if (parts.length === 3 && !lesson) return respond(response, 404, null, 'Lesson not found.');
            if (parts.length === 3 && lesson && lesson.status === 'ARCHIVED') return respond(response, 403, null, 'Restore this lesson before editing.');
            const payload = JSON.parse((await body(request, 400_000)).toString()) as DemoLessonPayload;
            const validationErrors = validateLesson({
              title: payload.title || '',
              content: payload.content ?? '',
              codeExample: parseCodeExample(payload.codeExample),
              externalLinks: parseExternalLinks(payload.externalLinks),
              completionMode: 'MANUAL',
              activityIds: [],
              requiredActivityIds: [],
            }, activities);
            if (Object.keys(validationErrors).length) return respond(response, 400, null, Object.values(validationErrors).join(' '));
            if (lesson && payload.completionMode === 'AUTO_ITEMS' && lesson.status === 'PUBLISHED' && lesson.ruleVersion === 0) {
              return respond(response, 400, null, 'Configure completion conditions before enabling automatic completion.');
            }
            const nextOrder = [...lessons.values()].filter(item => item.classroomId === classroom.id).length;
            const nextCompletionMode = lesson ? (payload.completionMode ?? lesson.completionMode) : (payload.completionMode ?? 'MANUAL');
            const saved: DemoLessonRecord = lesson
              ? {
                  ...lesson,
                  title: payload.title.trim(),
                  content: payload.content !== undefined ? payload.content : lesson.content,
                  codeExample: payload.codeExample !== undefined ? payload.codeExample : lesson.codeExample,
                  externalLinks: payload.externalLinks !== undefined ? payload.externalLinks : lesson.externalLinks,
                  completionMode: nextCompletionMode,
                  requiredItemIds: nextCompletionMode === 'AUTO_ITEMS' ? lesson.requiredItemIds : [],
                  updatedAt: timestamp(),
                }
              : {
                  ...seed(crypto.randomUUID(), payload.title.trim(), 'DRAFT', classroom.id, nextOrder),
                  content: payload.content ?? '',
                  codeExample: payload.codeExample ?? '',
                  externalLinks: payload.externalLinks ?? '[]',
                  completionMode: nextCompletionMode,
                };
            lessons.set(saved.id, saved);
            return respond(response, lesson ? 200 : 201, detailView(saved));
          }

          if (!lesson) return respond(response, 404, null, 'Lesson not found.');
          if (parts.length === 3 && method === 'GET') return respond(response, 200, detailView(lesson));
          if (parts[3] === 'progress' && method === 'GET') {
            return respond(
              response,
              isTeacher ? 200 : 403,
              {
                success: true,
                data: page([{ studentId: STUDENT, username: 'Demo Student', ...(progress.get(lesson.id) || { completed: false, completedAt: null }) }], pageIndex),
                timestamp: timestamp(),
              },
            );
          }
          if (parts[3] === 'complete' && method === 'POST') {
            if (isTeacher || lesson.completionMode !== 'MANUAL' || lesson.status !== 'PUBLISHED') {
              return respond(response, 403, null, 'Only students can mark a published manual lesson complete.');
            }
            progress.set(lesson.id, { completed: true, completedAt: timestamp() });
            return noContent(response);
          }
          if (parts[3] === 'materials') {
            if (!isTeacher || lesson.status === 'ARCHIVED') return respond(response, 403, null, 'This lesson is read-only.');
            if (parts.length === 4 && method === 'POST') {
              const { fileId } = JSON.parse((await body(request, 16_384)).toString()) as { fileId?: string };
              const stored = fileId ? files.get(fileId) : undefined;
              if (!stored) return respond(response, 400, null, 'Upload lesson material before attaching it.');
              const material: DemoMaterial = {
                id: crypto.randomUUID(),
                fileName: stored.name,
                fileUrl: `/api/v1/files/${stored.id}/content`,
                fileType: stored.kind,
                fileSize: stored.size,
                createdAt: timestamp(),
              };
              lesson.materials.push(material);
              return respond(response, 201, material);
            }
            if (parts.length === 5 && method === 'DELETE') {
              lesson.materials = lesson.materials.filter(item => item.id !== parts[4]);
              return noContent(response);
            }
          }
          if (parts[3] === 'items') {
            if (!isTeacher || lesson.status === 'ARCHIVED') return respond(response, 403, null, 'This lesson is read-only.');
            if (parts.length === 4 && method === 'POST') {
              const { itemType, itemId } = JSON.parse((await body(request, 16_384)).toString()) as { itemType: LessonActivity['type']; itemId: string };
              const option = activities.find(act => act.id === itemId && act.type === itemType);
              if (!option) return respond(response, 400, null, 'Linked item is unavailable in this classroom.');
              if (itemType === 'QUIZ' && [...lessons.values()].some(other => other.classroomId === classroom.id && other.id !== lesson.id && other.linkedItems.some(link => link.itemType === 'QUIZ' && link.itemId === itemId))) {
                return respond(response, 409, null, 'This quiz is already linked to another lesson.');
              }
              if (lesson.linkedItems.some(link => link.itemType === itemType && link.itemId === itemId)) {
                return respond(response, 400, null, 'Item is already linked.');
              }
              const linked: DemoLinkedItem = {
                id: crypto.randomUUID(),
                itemType,
                itemId,
                displayOrder: lesson.linkedItems.length,
                title: option.title,
                available: option.available,
              };
              lesson.linkedItems.push(linked);
              return respond(response, 201, linked);
            }
            if (parts.length === 5 && method === 'DELETE') {
              if (lesson.completionMode === 'AUTO_ITEMS' && lesson.requiredItemIds.includes(parts[4])) {
                return respond(response, 400, null, 'Update completion rule before unlinking this item.');
              }
              lesson.linkedItems = lesson.linkedItems.filter(link => link.itemId !== parts[4]);
              return noContent(response);
            }
          }
          if (parts[3] === 'completion-rule' && method === 'PUT') {
            if (!isTeacher || lesson.status === 'ARCHIVED') return respond(response, 403, null, 'This lesson is read-only.');
            const rule = JSON.parse((await body(request, 32_768)).toString()) as DemoCompletionRulePayload;
            if (!rule.conditions?.length || rule.conditions.some(c => c.itemType === 'PRACTICE' || !lesson.linkedItems.some(link => link.itemId === c.itemId && link.itemType === c.itemType))) {
              return respond(response, 400, null, 'Choose at least one linked assignment or quiz as a completion requirement.');
            }
            lesson.requiredItemIds = rule.conditions.map(c => c.itemId);
            lesson.ruleVersion += 1;
            lesson.completionMode = 'AUTO_ITEMS';
            lesson.updatedAt = timestamp();
            return respond(response, 200, null);
          }

          if (!isTeacher) return respond(response, 403, null, 'Only the teacher can change lesson state.');
          if (parts.length === 3 && method === 'DELETE') {
            if (!detailView(lesson).canDelete) return respond(response, 400, null, 'This lesson has learning data. Archive it instead.');
            lessons.delete(lesson.id);
            return noContent(response);
          }
          if (method === 'PATCH') {
            if (parts[3] === 'archive' && lesson.status !== 'ARCHIVED') {
              lesson.previousStatus = lesson.status;
              lesson.status = 'ARCHIVED';
            } else if (parts[3] === 'restore' && lesson.status === 'ARCHIVED') {
              lesson.status = lesson.previousStatus || 'DRAFT';
              lesson.previousStatus = null;
            } else if (parts[3] === 'publish' && lesson.status === 'DRAFT') {
              if (lesson.completionMode === 'AUTO_ITEMS' && lesson.ruleVersion === 0) {
                return respond(response, 400, null, 'Configure completion conditions before publishing.');
              }
              lesson.status = 'PUBLISHED';
            } else if (parts[3] === 'unpublish' && lesson.status === 'PUBLISHED') {
              lesson.status = 'DRAFT';
            } else {
              return respond(response, 400, null, 'This state transition is not available.');
            }
            lesson.updatedAt = timestamp();
            return respond(response, 200, detailView(lesson));
          }
          respond(response, 405, null, 'This demo action is not available.');
        } catch {
          respond(response, 400, null, 'The demo could not read this request. Your saved lesson is preserved.');
        }
      });
    },
  };
}
