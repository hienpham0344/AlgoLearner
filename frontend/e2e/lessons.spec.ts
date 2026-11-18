import { expect, test, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import type { CodeExample, ExternalLink, Lesson, LessonActivity } from '../src/features/classroom/lessons/types';

// These tests exercise the FE against the confirmed #12 backend LessonController contract.
const base = 'http://127.0.0.1:3101';
const classId = 'class-a';
const teacherId = 'teacher-a';
const time = '2026-11-11T00:00:00Z';
const lessonPath = (role = 'teacher', id = '') => `${base}/${role}/classrooms/${classId}/lessons${id ? `/${id}` : ''}`;
const pageData = <T,>(items: T[], page = 0, totalPages = 1) => ({ items, pagination: { page, size: 20, totalElements: items.length, totalPages, isFirst: page === 0, isLast: page + 1 >= totalPages } });
const toBackendMode = (mode: Lesson['completionMode']) => (mode === 'CONDITION_BASED' ? 'AUTO_ITEMS' : 'MANUAL');
const fileTypeFromName = (name: string): 'PDF' | 'DOCX' | 'PPTX' | 'ZIP' | 'IMAGE' => {
  const ext = name.split('.').pop()?.toLowerCase();
  if (ext === 'docx') return 'DOCX';
  if (ext === 'pptx') return 'PPTX';
  if (ext === 'zip') return 'ZIP';
  if (ext === 'png' || ext === 'jpg' || ext === 'jpeg' || ext === 'gif' || ext === 'webp') return 'IMAGE';
  return 'PDF';
};
const parseCodeExample = (raw: string | null | undefined): CodeExample | null => {
  if (!raw?.trim()) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<CodeExample>;
    return parsed.code ? { language: parsed.language || 'text', code: parsed.code } : null;
  } catch {
    return { language: 'text', code: raw };
  }
};
const parseExternalLinks = (raw: string | null | undefined): ExternalLink[] => {
  if (!raw?.trim()) return [];
  try {
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed) ? parsed as ExternalLink[] : [];
  } catch {
    return [];
  }
};
const toSummary = (lesson: Lesson) => ({
  id: lesson.id,
  classroomId: lesson.classroomId,
  title: lesson.title,
  status: lesson.status,
  completionMode: toBackendMode(lesson.completionMode),
  displayOrder: 0,
  isCompleted: lesson.progress ? lesson.progress.completed : null,
  completedAt: lesson.progress ? lesson.progress.completedAt : null,
  createdAt: lesson.createdAt,
  updatedAt: lesson.updatedAt,
});
const toDetail = (lesson: Lesson) => ({
  id: lesson.id,
  classroomId: lesson.classroomId,
  title: lesson.title,
  content: lesson.content,
  codeExample: lesson.codeExample ? JSON.stringify(lesson.codeExample) : '',
  externalLinks: JSON.stringify(lesson.externalLinks),
  status: lesson.status,
  completionMode: toBackendMode(lesson.completionMode),
  displayOrder: 0,
  materials: lesson.attachments.map(att => ({
    id: att.id,
    fileName: att.name,
    fileUrl: `/api/v1/files/stored-${att.id}/content`,
    fileType: fileTypeFromName(att.name),
    fileSize: att.size,
    createdAt: time,
  })),
  linkedItems: lesson.activities.map((act, idx) => ({
    id: `link-${act.id}`,
    itemType: act.type,
    itemId: act.id,
    displayOrder: idx,
    title: act.title,
    available: act.available,
  })),
  isCompleted: lesson.progress ? lesson.progress.completed : null,
  completedAt: lesson.progress ? lesson.progress.completedAt : null,
  createdAt: lesson.createdAt,
  updatedAt: lesson.updatedAt,
  previousStatus: lesson.previousStatus,
  canDelete: lesson.canDelete,
  requiredItemIds: lesson.requiredActivityIds,
});

const activity: LessonActivity = { id: 'quiz-a', title: 'Recursion quiz', type: 'QUIZ', linkedLessonId: null, available: true, path: null, completed: false };
const seed = (overrides: Partial<Lesson> = {}): Lesson => ({
  id: 'lesson-a', classroomId: classId, title: 'Understanding recursion', content: '# The recursive idea\nOne step at a time.\n<script>window.lessonXss = true</script>\n<img src=x onerror="window.lessonXss=true">',
  codeExample: { language: 'java', code: 'int sum(int n) { return n == 0 ? 0 : n + sum(n - 1); }' },
  externalLinks: [{ label: 'Reference guide', url: 'https://example.org/recursion' }], completionMode: 'MANUAL', requiredActivityIds: [],
  activities: [], attachments: [{ id: 'file-a', name: 'recursion.pdf', size: 2048, contentType: 'application/pdf' }], progress: null,
  status: 'DRAFT', previousStatus: null, canDelete: false, createdAt: time, updatedAt: time, ...overrides,
});

interface FixtureOptions { role?: 'TEACHER' | 'STUDENT' | 'ADMIN'; lessons?: Lesson[]; ownerId?: string; membershipDenied?: boolean; apiStatus?: number; policyAvailable?: boolean }
async function fixture(page: Page, options: FixtureOptions = {}) {
  const role = options.role || 'TEACHER';
  const user = { id: role === 'TEACHER' ? teacherId : 'student-a', username: role.toLowerCase(), fullName: `Lesson ${role.toLowerCase()}`, email: 'lesson@example.test', role, lifecycleStatus: 'ACTIVE' };
  const state = { lessons: options.lessons || [seed()], calls: [] as string[], completionFails: false, downloadFails: false, uploadFails: false,
    denied: false, deleteFails: false, updateFails: false, unconfirmedCompletion: false, activityOptions: [activity,
      { ...activity, id: 'assignment-a', type: 'ASSIGNMENT' as const, title: 'Recursion assignment' },
      { ...activity, id: 'practice-a', type: 'PRACTICE' as const, title: 'Recursion practice' },
      { ...activity, id: 'quiz-b', title: 'Already linked quiz', linkedLessonId: 'lesson-other' }] };
  await page.addInitScript(({ user }) => { localStorage.setItem('algolearner_token', 'lesson-test-token'); localStorage.setItem('algolearner_user', JSON.stringify(user)); }, { user });
  await page.route('**/api/v1/**', async route => {
    const request = route.request(); const url = new URL(request.url()); const path = url.pathname.replace('/api/v1', ''); const method = request.method();
    state.calls.push(`${method} ${path}`);
    const ok = (data: unknown, status = 200) => route.fulfill({ status, json: { success: true, data, timestamp: time } });
    const fail = (status: number, message = 'Fixture request denied') => route.fulfill({ status, json: { success: false, errorCode: 'TEST_FAILURE', message, timestamp: time, path } });
    if (path === '/auth/me') return ok(user);
    if (path === `/classrooms/${classId}`) return options.membershipDenied || state.denied ? fail(403) : ok({ id: classId, name: 'Algorithms · Autumn 2026', status: 'ACTIVE', ownerId: options.ownerId || teacherId });
    const root = `/classrooms/${classId}/lessons`;
    if (path === `${root}/capabilities` && method === 'GET') return options.policyAvailable === false ? fail(503) : ok({ uploadsAvailable: true, activityLinkingAvailable: true, maxFileSizeBytes: 1024 * 1024, allowedExtensions: ['pdf', 'docx', 'pptx', 'zip', 'png'] });
    if (path === `${root}/activities` && method === 'GET') return ok(state.activityOptions.map(({ id, type, title, linkedLessonId, available }) => ({ id, type, title, linkedLessonId, available })));
    if (state.denied) return fail(403);
    if (options.apiStatus) return fail(options.apiStatus);
    if (path === `/classrooms/${classId}/files` && method === 'POST') {
      if (state.uploadFails) return fail(503, 'Storage unavailable');
      return ok({ id: 'stored-new-file', name: 'notes.pdf', mediaType: 'application/pdf', kind: 'PDF', size: 10, purpose: 'LESSON_MATERIAL', state: 'READY', sha256: 'fixture', createdAt: time }, 201);
    }
    if (path.startsWith('/files/') && path.endsWith('/content') && method === 'GET') {
      return state.downloadFails ? fail(503, 'Storage timeout') : route.fulfill({ status: 200, contentType: 'application/pdf', body: 'PDF fixture content' });
    }
    if (path === root && method === 'GET') return ok(state.lessons.map(toSummary));
    if (path === root && method === 'POST') {
      const payload = request.postDataJSON() as { title: string; content: string; codeExample: string; externalLinks: string; completionMode: 'MANUAL' | 'AUTO_ITEMS' | 'NONE' };
      const created = seed({
        id: 'created-lesson',
        title: payload.title,
        content: payload.content,
        codeExample: parseCodeExample(payload.codeExample),
        externalLinks: parseExternalLinks(payload.externalLinks),
        completionMode: payload.completionMode === 'AUTO_ITEMS' ? 'CONDITION_BASED' : 'MANUAL',
        requiredActivityIds: [],
        activities: [],
        attachments: [],
        canDelete: true,
      });
      state.lessons.push(created); return ok(toDetail(created), 201);
    }
    if (!path.startsWith(`${root}/`)) return fail(404);
    const parts = path.slice(root.length + 1).split('/');
    const lesson = state.lessons.find(item => item.id === parts[0]);
    if (!lesson) return fail(404);
    if (role === 'STUDENT' && lesson.status !== 'PUBLISHED') return fail(403);
    if (parts.length === 1 && method === 'GET') return ok(toDetail(lesson));
    if (parts.length === 1 && method === 'PUT') {
      if (state.updateFails) return fail(409, 'This quiz already belongs to another lesson.');
      const payload = request.postDataJSON() as { title: string; content: string; codeExample: string; externalLinks: string; completionMode: 'MANUAL' | 'AUTO_ITEMS' | 'NONE' };
      Object.assign(lesson, {
        title: payload.title,
        content: payload.content,
        codeExample: parseCodeExample(payload.codeExample),
        externalLinks: parseExternalLinks(payload.externalLinks),
        completionMode: payload.completionMode === 'AUTO_ITEMS' ? 'CONDITION_BASED' : 'MANUAL',
      });
      return ok(toDetail(lesson));
    }
    if (parts.length === 1 && method === 'DELETE') {
      if (!lesson.canDelete || state.deleteFails) return fail(409, 'Related data must be preserved. Archive this lesson.');
      state.lessons = state.lessons.filter(item => item.id !== lesson.id); return route.fulfill({ status: 204 });
    }
    if (parts[1] === 'progress' && method === 'GET') return ok(pageData([{ studentId: 'student-a', username: 'Student A', completed: true, completedAt: time }]));
    if (parts[1] === 'complete' && method === 'POST') {
      if (state.completionFails) return fail(503);
      if (!state.unconfirmedCompletion) {
        lesson.progress = { completed: true, completedAt: time };
      }
      return route.fulfill({ status: 204 });
    }
    if (parts[1] === 'materials') {
      if (method === 'POST') {
        if (state.uploadFails) return fail(503, 'Storage unavailable');
        const file = { id: 'new-file', name: 'notes.pdf', size: 10, contentType: 'application/pdf' };
        lesson.attachments.push(file);
        return ok({ id: file.id, fileName: file.name, fileUrl: '/api/v1/files/stored-new-file/content', fileType: 'PDF', fileSize: file.size, createdAt: time }, 201);
      }
      if (method === 'DELETE') { lesson.attachments = lesson.attachments.filter(file => file.id !== parts[2]); return route.fulfill({ status: 204 }); }
    }
    if (parts[1] === 'items') {
      if (method === 'POST') {
        const { itemId } = request.postDataJSON() as { itemType: LessonActivity['type']; itemId: string };
        const option = state.activityOptions.find(opt => opt.id === itemId);
        if (!option) return fail(404);
        if (!lesson.activities.some(act => act.id === itemId)) lesson.activities.push(option);
        return ok({ id: `link-${option.id}`, itemType: option.type, itemId: option.id, displayOrder: lesson.activities.length - 1, title: option.title, available: option.available }, 201);
      }
      if (method === 'DELETE') { lesson.activities = lesson.activities.filter(act => act.id !== parts[2]); return route.fulfill({ status: 204 }); }
    }
    if (parts[1] === 'completion-rule' && method === 'PUT') {
      const rule = request.postDataJSON() as { operator: 'ALL' | 'ANY'; conditions: Array<{ itemId: string }> };
      lesson.completionMode = 'CONDITION_BASED';
      lesson.requiredActivityIds = rule.conditions.map(c => c.itemId);
      return ok(null);
    }
    if (method === 'PATCH' && parts[1] === 'publish') lesson.status = 'PUBLISHED';
    else if (method === 'PATCH' && parts[1] === 'unpublish') lesson.status = 'DRAFT';
    else if (method === 'PATCH' && parts[1] === 'archive') { lesson.previousStatus = lesson.status === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT'; lesson.status = 'ARCHIVED'; }
    else if (method === 'PATCH' && parts[1] === 'restore') lesson.status = lesson.previousStatus || 'DRAFT';
    else return fail(404);
    return ok(toDetail(lesson));
  });
  return state;
}

test('LES-10-01/02/03: create, safe preview, link activities and save a draft', async ({ page }) => {
  const state = await fixture(page, { lessons: [] });
  await page.goto(lessonPath());
  await expect(page.getByText('A good lesson starts with an idea.')).toBeVisible();
  await page.getByRole('link', { name: 'Create lesson' }).click();
  await page.getByLabel('Lesson title').fill('A new recursion lesson');
  await page.getByLabel('Theory & notes').fill('# First idea\n<script>window.lessonXss=true</script>\n**Think recursively**');
  await page.getByLabel('Example source code').fill('print("hello")');
  await page.getByRole('button', { name: 'Add link' }).click();
  await page.getByLabel('Link 1 label').fill('Guide'); await page.getByLabel('Link 1 URL').fill('https://example.org/guide');
  await expect(page.getByRole('checkbox', { name: /Already linked quiz/ })).toBeDisabled();
  await page.getByRole('checkbox', { name: /Recursion quiz/ }).check();
  await page.getByRole('checkbox', { name: /Recursion assignment/ }).check();
  await page.getByRole('checkbox', { name: /Recursion practice/ }).check();
  await page.getByLabel('How do students complete').selectOption('CONDITION_BASED');
  await page.getByLabel('Require Recursion quiz').check();
  await page.getByRole('button', { name: 'Preview', exact: true }).click();
  await expect(page.getByText('Think recursively', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => Reflect.get(window, 'lessonXss'))).toBeUndefined();
  await page.getByRole('button', { name: 'Save draft' }).click();
  await expect(page).toHaveURL(/\/created-lesson$/);
  await expect(page.getByRole('heading', { name: 'A new recursion lesson' })).toBeVisible();
  expect(state.lessons[0].status).toBe('DRAFT');
  expect(state.lessons[0].requiredActivityIds).toEqual(['quiz-a']);
  expect(state.lessons[0].activities.map(item => item.type)).toEqual(['QUIZ', 'ASSIGNMENT', 'PRACTICE']);
});

test('LES-10-04/05/06/08: publish, draft, archive, restore preserve recorded progress', async ({ page }) => {
  const state = await fixture(page, { lessons: [seed({ progress: { completed: true, completedAt: time } })] });
  await page.goto(lessonPath('teacher', 'lesson-a'));
  await expect(page.getByRole('button', { name: 'Delete lesson', exact: true })).toBeDisabled();
  const transition = async (name: string) => { await page.getByRole('button', { name, exact: true }).click(); await expect(page.getByRole('dialog')).toBeVisible(); await page.getByRole('button', { name: 'Confirm', exact: true }).click(); await expect(page.getByRole('dialog')).not.toBeVisible(); };
  await transition('Publish lesson'); await expect(page.locator('.lesson-status')).toHaveText('Published');
  await transition('Move to draft'); await expect(page.locator('.lesson-status')).toHaveText('Draft');
  await transition('Archive lesson'); await expect(page.locator('.lesson-status')).toHaveText('Archived');
  await expect(page.getByRole('button', { name: 'Edit lesson' })).toHaveCount(0);
  await transition('Restore lesson'); await expect(page.locator('.lesson-status')).toHaveText('Draft');
  expect(state.lessons[0].progress).toEqual({ completed: true, completedAt: time });
  await expect(page.getByRole('region', { name: 'Student progress' })).toContainText('Student A');
  await expect(page.getByRole('region', { name: 'Student progress' })).toContainText('07:00');
});

test('LES-10-07: delete a lesson without protected data', async ({ page }) => {
  const state = await fixture(page, { lessons: [seed({ canDelete: true, attachments: [] })] });
  await page.goto(lessonPath('teacher', 'lesson-a'));
  await page.getByRole('button', { name: 'Delete lesson', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(page).toHaveURL(/\/lessons$/); expect(state.lessons).toHaveLength(0);
});

test('LES-01-01/02/07: safe student reading, download failure, manual completion and reload', async ({ page }, testInfo) => {
  const state = await fixture(page, { role: 'STUDENT', lessons: [seed({ status: 'PUBLISHED' })] });
  state.downloadFails = true;
  await page.goto(lessonPath('student', 'lesson-a'));
  await expect(page.getByText('One step at a time.')).toBeVisible();
  await expect(page.locator('.lesson-content script, .lesson-content img')).toHaveCount(0);
  await expect(page.getByRole('link', { name: /Reference guide/ })).toHaveAttribute('rel', 'noopener noreferrer');
  await page.getByRole('button', { name: 'Download recursion.pdf' }).click();
  await expect(page.getByRole('alert')).toContainText('Download failed');
  await expect(page.getByText('One step at a time.')).toBeVisible();
  await page.getByRole('button', { name: 'Mark as completed', exact: true }).click();
  await expect(page.locator('.lesson-completion')).toContainText('Completed');
  await page.reload(); await expect(page.locator('.lesson-completion')).toContainText('Completed');
  expect((await new AxeBuilder({ page }).include('.lesson-shell').withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()).violations).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath('lesson-student.png'), fullPage: true });
  expect(state.calls.some(call => call.endsWith('/progress'))).toBe(false);
});

test('LES-01-03: conditional completion comes only from a refreshed server result', async ({ page }) => {
  const lesson = seed({ status: 'PUBLISHED', completionMode: 'CONDITION_BASED', activities: [activity], requiredActivityIds: ['quiz-a'] });
  const state = await fixture(page, { role: 'STUDENT', lessons: [lesson] });
  await page.goto(lessonPath('student', 'lesson-a'));
  await expect(page.getByRole('button', { name: 'Mark as completed' })).toHaveCount(0);
  await expect(page.getByText('Not completed yet.', { exact: false })).toBeVisible();
  state.lessons[0].progress = { completed: true, completedAt: time };
  await page.getByRole('button', { name: 'Refresh completion' }).click();
  await expect(page.locator('.lesson-completion')).toContainText('Completed');
  expect(state.calls.some(call => call.startsWith('POST'))).toBe(false);
});

for (const status of ['DRAFT', 'ARCHIVED'] as const) test(`LES-01-04/05: student cannot read ${status} via URL`, async ({ page }) => {
  await fixture(page, { role: 'STUDENT', lessons: [seed({ status })] });
  await page.goto(lessonPath('student', 'lesson-a'));
  await expect(page.getByRole('alert')).toContainText('permission');
  await expect(page.getByText('One step at a time.')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Download recursion.pdf' })).toHaveCount(0);
});

test('LES-01-06 / LES-10-10: outside membership and non-owner teacher fail closed', async ({ page }) => {
  await fixture(page, { role: 'STUDENT', membershipDenied: true });
  await page.goto(lessonPath('student', 'lesson-a'));
  await expect(page.getByRole('alert')).toContainText('permission');
  await expect(page.getByText('One step at a time.')).toHaveCount(0);
});

test('LES-10-10: teacher who does not own the class makes no lesson request', async ({ page }) => {
  const state = await fixture(page, { ownerId: 'another-teacher' });
  await page.goto(lessonPath('teacher', 'lesson-a'));
  await expect(page.getByRole('alert')).toContainText('Only the classroom owner');
  expect(state.calls.some(call => call.includes('/lessons/'))).toBe(false);
});

test('LES-10-09 / LES-01-07: file validation, storage errors and retry preserve saved text', async ({ page }) => {
  const state = await fixture(page); await page.goto(lessonPath('teacher', 'lesson-a'));
  await expect(page.getByLabel('Attach a document or image')).toBeEnabled();
  await page.getByLabel('Attach a document or image').setInputFiles({ name: 'bad.exe', mimeType: 'application/octet-stream', buffer: Buffer.from('bad') });
  await expect(page.getByRole('alert')).toContainText('not allowed');
  expect(state.calls.filter(call => call.startsWith('POST') && (call.endsWith('/files') || call.endsWith('/materials')))).toHaveLength(0);
  state.uploadFails = true;
  await page.getByLabel('Attach a document or image').setInputFiles({ name: 'notes.pdf', mimeType: 'application/pdf', buffer: Buffer.from('notes') });
  await expect(page.getByRole('alert')).toContainText('Upload failed');
  await expect(page.getByText('One step at a time.')).toBeVisible();
  state.uploadFails = false;
  await page.getByLabel('Attach a document or image').setInputFiles({ name: 'notes.pdf', mimeType: 'application/pdf', buffer: Buffer.from('notes') });
  await expect(page.getByRole('button', { name: 'Download notes.pdf' })).toBeVisible();
});

test('missing storage and activity capabilities keep content editing usable', async ({ page }) => {
  await fixture(page, { policyAvailable: false }); await page.goto(lessonPath('teacher', 'lesson-a'));
  await expect(page.getByLabel('Attach a document or image')).toBeDisabled();
  await page.getByRole('button', { name: 'Edit lesson' }).click();
  await page.getByLabel('Lesson title').fill('Retained edits');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('heading', { name: 'Retained edits' })).toBeVisible();
});

test('API 503 shows retry without seeded lesson fallback', async ({ page }) => {
  await fixture(page, { apiStatus: 503 }); await page.goto(lessonPath());
  await expect(page.getByRole('alert')).toContainText('unavailable');
  await expect(page.getByRole('button', { name: 'Try again' })).toBeVisible();
  await expect(page.getByText('Understanding recursion')).toHaveCount(0);
});

test('completion failure never records local success', async ({ page }) => {
  const state = await fixture(page, { role: 'STUDENT', lessons: [seed({ status: 'PUBLISHED' })] }); state.completionFails = true;
  await page.goto(lessonPath('student', 'lesson-a')); await page.getByRole('button', { name: 'Mark as completed', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('unavailable');
  await expect(page.getByRole('button', { name: 'Mark as completed', exact: true })).toBeEnabled();
  expect(state.lessons[0].progress).toBeNull();
});

test('LES-10-08: stale delete eligibility never bypasses the server conflict', async ({ page }) => {
  const state = await fixture(page, { lessons: [seed({ canDelete: true })] }); state.deleteFails = true;
  await page.goto(lessonPath('teacher', 'lesson-a')); await page.getByRole('button', { name: 'Delete lesson', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Related data');
  await expect(page.getByRole('heading', { name: 'Understanding recursion' })).toBeVisible();
  expect(state.lessons).toHaveLength(1);
});

test('LES-10-11: rejected save keeps edited content and existing quiz links', async ({ page }) => {
  const state = await fixture(page); state.updateFails = true;
  await page.goto(lessonPath('teacher', 'lesson-a')); await page.getByRole('button', { name: 'Edit lesson' }).click();
  await page.getByLabel('Lesson title').fill('Do not lose this edit');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('alert')).toContainText('quiz already belongs');
  await expect(page.getByLabel('Lesson title')).toHaveValue('Do not lose this edit');
  expect(state.lessons[0].title).toBe('Understanding recursion');
});

test('completion success requires an explicit completed result from the server', async ({ page }) => {
  const state = await fixture(page, { role: 'STUDENT', lessons: [seed({ status: 'PUBLISHED' })] }); state.unconfirmedCompletion = true;
  await page.goto(lessonPath('student', 'lesson-a')); await page.getByRole('button', { name: 'Mark as completed', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('did not confirm completion');
  await expect(page.getByText('Your completion has been saved.')).toHaveCount(0);
});

test('401 requires sign in and does not render cached lesson content', async ({ page }) => {
  await fixture(page, { apiStatus: 401 }); await page.goto(lessonPath('teacher', 'lesson-a'));
  await expect(page).toHaveURL(/\/login$/); await expect(page.getByText('One step at a time.')).toHaveCount(0);
});

test('role mismatch prevents all lesson requests', async ({ page }) => {
  const state = await fixture(page, { role: 'ADMIN' }); await page.goto(lessonPath('teacher', 'lesson-a'));
  await expect(page.getByRole('alert')).toContainText('teacher accounts');
  expect(state.calls.some(call => call.includes('/lessons'))).toBe(false);
});

test('student list hides draft and archived entries even in an over-broad response', async ({ page }) => {
  await fixture(page, { role: 'STUDENT', lessons: [seed({ id: 'draft', title: 'Private draft' }), seed({ id: 'archived', title: 'Private archive', status: 'ARCHIVED' }), seed({ status: 'PUBLISHED' })] });
  await page.goto(lessonPath('student')); await expect(page.getByRole('heading', { name: 'Understanding recursion' })).toBeVisible();
  await expect(page.getByText('Private draft')).toHaveCount(0); await expect(page.getByText('Private archive')).toHaveCount(0);
});

test('preview validation focuses the error and does not submit an empty hidden title', async ({ page }) => {
  const state = await fixture(page, { lessons: [] }); await page.goto(lessonPath('teacher', 'new'));
  await page.getByRole('button', { name: 'Preview', exact: true }).click();
  await page.getByRole('button', { name: 'Save draft' }).click();
  await expect(page.getByRole('alert')).toContainText('lesson title');
  await expect(page.getByRole('alert')).toBeFocused();
  expect(state.calls.some(call => call.startsWith('POST'))).toBe(false);
});

test('lesson page and editor are accessible at mobile width and keyboard usable', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await fixture(page); await page.goto(lessonPath('teacher', 'lesson-a'));
  await expect(page.getByRole('heading', { name: 'Understanding recursion' })).toBeVisible({ timeout: 15_000 });
  expect((await new AxeBuilder({ page }).include('.lesson-shell').withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()).violations).toEqual([]);
  const overflow = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth,
    elements: Array.from(document.querySelectorAll('body *')).filter(element => element.getBoundingClientRect().right > innerWidth + 1).map(element => ({ tag: element.tagName, class: element.className, width: element.getBoundingClientRect().width })).slice(0, 15) }));
  expect(overflow.scroll, JSON.stringify(overflow)).toBeLessThanOrEqual(overflow.width);
  await page.screenshot({ path: testInfo.outputPath('lesson-mobile.png'), fullPage: true });
  await page.getByRole('button', { name: 'Edit lesson' }).focus(); await page.keyboard.press('Enter');
  await expect(page.getByLabel('Lesson title')).toBeVisible();
  expect((await new AxeBuilder({ page }).include('.lesson-shell').withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()).violations).toEqual([]);
  await page.setViewportSize({ width: 1440, height: 1000 });
  expect((await new AxeBuilder({ page }).include('.lesson-shell').withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()).violations).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath('lesson-editor-desktop.png'), fullPage: true });
});
