import { expect, test, type Page } from '@playwright/test';
import { createServer } from 'node:http';

const base = 'http://127.0.0.1:3100';
async function demoLogin(page: Page) {
  await page.goto(`${base}/login`);
  await expect(page.getByText('Backend chưa chạy', { exact: false })).toBeVisible();
  await page.getByLabel('Username or email').fill('learner@example.test');
  await page.getByLabel('Password', { exact: true }).fill('DemoPass123!');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });
}

test('offline mock supports profile reload and classrooms with a visible notice', async ({ page }) => {
  await demoLogin(page);
  await page.goto(`${base}/profile`);
  await expect(page.getByText('Backend chưa chạy', { exact: false })).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Display name' })).toHaveValue('Learner');
  await page.reload();
  await expect(page).toHaveURL(/\/profile/);
  await page.goto(`${base}/student/classrooms`);
  await expect(page.getByText('Backend chưa chạy', { exact: false })).toBeVisible();
  await expect(page.getByRole('heading').first()).toBeVisible();
});

test('backend recovery ends demo session and real login errors never fall back to demo', async ({ page }) => {
  await demoLogin(page);
  const backend = createServer((req, res) => {
    req.resume();
    const status = req.url === '/api/v1/status' ? 200 : 401;
    res.writeHead(status, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(status === 200 ? { success: true, data: { source: 'backend' } }
      : { success: false, errorCode: 'INVALID_CREDENTIALS', message: 'Real backend rejected credentials.' }));
  });
  await new Promise<void>((resolve, reject) => { backend.once('error', reject); backend.listen(19999, '127.0.0.1', resolve); });
  try {
    await expect(page).toHaveURL(/\/login/, { timeout: 15_000 });
    await expect(page.getByText('Backend chưa chạy', { exact: false })).toHaveCount(0);
    await page.getByLabel('Username or email').fill('teacher');
    await page.getByLabel('Password', { exact: true }).fill('DemoPass123!');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page.getByText('Real backend rejected credentials.')).toBeVisible();
    expect(await page.evaluate(() => localStorage.getItem('algolearner_token'))).toBeNull();
  } finally { await new Promise<void>(resolve => { backend.closeAllConnections(); backend.close(() => resolve()); }); }
});

test('available backend supplies account, profile, dashboard and classrooms', async ({ page }) => {
  const timestamp = '2026-11-11T00:00:00Z';
  const account = { id: 'backend-teacher', username: 'backend-teacher', email: 'backend@example.test',
    role: 'TEACHER', lifecycleStatus: 'ACTIVE' };
  const empty = { items: [], totalElements: 0, hasMore: false };
  const classroom = { id: 'backend-classroom', name: 'Classroom from backend', description: 'Persisted classroom data',
    status: 'ACTIVE', joinMode: 'AUTO_JOIN' };
  const calls = new Set<string>();
  let classroomListCalls = 0;
  let currentUserCalls = 0;
  let classroomUnavailable = false;
  const backend = createServer((req, res) => {
    req.resume();
    const path = req.url?.split('?')[0] ?? '';
    calls.add(path);
    const send = (status: number, data: object) => {
      res.writeHead(status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(status === 200 ? { success: true, data, timestamp } : { success: false, ...data, timestamp }));
    };
    if (path === '/api/v1/status') return send(200, { source: 'backend' });
    if (path === '/api/v1/auth/login') return send(200, { token: 'backend-access', user: account });
    if (req.headers.authorization !== 'Bearer backend-access') return send(401, { message: 'Authentication required' });
    if (path === '/api/v1/auth/me') { currentUserCalls += 1; return send(200, account); }
    if (path === '/api/v1/users/me/profile') return send(200, { ...account, fullName: 'Teacher from backend',
      shortBio: 'Profile from backend', avatarUrl: null, joinedAt: timestamp, isPublic: false,
      publicPracticeStatistics: { solved: 0 }, streak: { currentStreak: 0, highestStreak: 0 } });
    if (path === '/api/v1/dashboards/teacher') return send(200, { role: 'TEACHER', generatedAt: timestamp,
      displayTimeZone: 'Asia/Ho_Chi_Minh', classrooms: { items: [{ classroomId: classroom.id, name: classroom.name }], totalElements: 1, hasMore: false },
      openOrUpcomingAssignments: empty, ungradedSubmissions: empty, recentSubmissions: empty,
      recentNotifications: empty, pendingContributions: { courseCount: 0, problemCount: 0 }, classroomStatistics: empty });
    if (path === '/api/v1/classrooms') {
      classroomListCalls += 1;
      if (classroomUnavailable) return send(503, { message: 'Backend database unavailable' });
      return send(200, { items: [classroom], pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1, isFirst: true, isLast: true } });
    }
    send(404, { message: 'Unknown backend route' });
  });
  await new Promise<void>((resolve, reject) => { backend.once('error', reject); backend.listen(19999, '127.0.0.1', resolve); });
  try {
    await page.goto(`${base}/login`);
    await page.getByLabel('Username or email').fill(account.email);
    await page.getByLabel('Password', { exact: true }).fill('BackendPass123!');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await page.getByRole('button', { name: 'Continue to your workspace' }).click();
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });
    await expect(page.getByText(classroom.name, { exact: true })).toBeVisible();
    expect(await page.evaluate(() => localStorage.getItem('algolearner_token'))).toBe('backend-access');
    await page.goto(`${base}/profile`);
    await expect(page.getByRole('textbox', { name: 'Display name' })).toHaveValue('Teacher from backend');
    expect(currentUserCalls).toBe(1);
    await page.goto(`${base}/teacher/classrooms`);
    await expect(page.getByRole('heading', { name: classroom.name })).toBeVisible();
    expect(classroomListCalls).toBe(1);
    await expect(page.getByText('Backend chưa chạy', { exact: false })).toHaveCount(0);
    for (const path of ['/auth/login', '/auth/me', '/users/me/profile', '/dashboards/teacher', '/classrooms']) {
      expect(calls.has(`/api/v1${path}`)).toBe(true);
    }
    classroomUnavailable = true;
    await page.reload();
    await expect(page.getByRole('alert')).toContainText('Backend database unavailable');
    await expect(page.getByRole('heading', { name: classroom.name })).toHaveCount(0);
    await expect(page.getByText('Backend chưa chạy', { exact: false })).toHaveCount(0);
  } finally { await new Promise<void>(resolve => { backend.closeAllConnections(); backend.close(() => resolve()); }); }
});
