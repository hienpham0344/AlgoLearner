import { expect, test, type Page } from '@playwright/test';
import { writeFile } from 'node:fs/promises';

test.beforeEach(async ({ page }) => {
  await page.coverage.startJSCoverage({ resetOnNavigation: false });
});

test.afterEach(async ({ page }, testInfo) => {
  const entries = await page.coverage.stopJSCoverage();
  const source = entries.filter(entry => entry.url.startsWith('http') && /\/src\/|\/dev\//.test(new URL(entry.url).pathname));
  await writeFile(testInfo.outputPath('source-coverage.json'), JSON.stringify(source.map(entry => ({
    path: new URL(entry.url).pathname,
    functions: entry.functions.map(fn => ({
      start: fn.ranges[0]?.startOffset ?? 0,
      end: fn.ranges[0]?.endOffset ?? 0,
      hit: (fn.ranges[0]?.count ?? 0) > 0,
    })),
  }))));
});

async function signIn(page: Page, base: string, username: string) {
  if (page.url() !== `${base}/login`) await page.goto(`${base}/login`);
  await page.getByLabel('Username or email').fill(username);
  await page.getByLabel('Password', { exact: true }).fill('DemoPass123!');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'You’re all set.' })).toBeVisible();
  await page.getByRole('button', { name: 'Continue to your workspace' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

test('4.2 student dashboard and profile work in demo mode', async ({ page }) => {
  await signIn(page, 'http://127.0.0.1:3100', 'learner@example.test');
  await expect(page.getByRole('heading', { name: 'Keep building.' })).toBeVisible();
  await page.goto('http://127.0.0.1:3100/profile');
  await expect(page.getByRole('heading', { name: 'Personal information' })).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Display name' })).toHaveValue('Learner');
  await page.getByRole('textbox', { name: 'Display name' }).fill('E2E Learner');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('textbox', { name: 'Display name' })).toHaveValue('E2E Learner');
  await page.getByRole('tab', { name: 'Privacy' }).click();
  await expect(page.getByRole('heading', { name: 'Public profile' })).toBeVisible();
  await page.locator('label.folder-switch').click();
  await expect(page.getByRole('status')).toContainText('Your public profile is visible.');
});

test('4.2 profile reads the backend response in backend-mock mode', async ({ page }) => {
  const base = 'http://127.0.0.1:3101';
  let profileRequests = 0;
  await page.route('**/api/v1/users/me/profile', route => {
    profileRequests++;
    return route.fulfill({ json: { success: true, data: {
      fullName: 'Backend Profile', username: 'teacher', email: 'teacher@example.test', avatarUrl: null,
      shortBio: 'Loaded from API', role: 'TEACHER', joinedAt: '2026-10-01T00:00:00Z',
      isPublic: false, publicPracticeStatistics: { solved: 0 }, streak: { currentStreak: 0, highestStreak: 0 },
    }, timestamp: '2026-11-09T00:00:00Z' } });
  });
  await signIn(page, base, 'teacher');
  await page.goto(`${base}/profile`);
  await expect(page.getByRole('textbox', { name: 'Display name' })).toHaveValue('Backend Profile');
  expect(profileRequests).toBeGreaterThan(0);
});

test('4.3 teacher classroom prototype opens and creates a classroom', async ({ page }) => {
  await signIn(page, 'http://127.0.0.1:3100', 'teacher@example.test');
  await page.goto('http://127.0.0.1:3100/teacher/classrooms');
  await expect(page.getByRole('heading', { name: 'Classrooms & Teaching Spaces' })).toBeVisible();
  await page.getByRole('button', { name: 'Create Class', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('dialog').getByLabel('Classroom Name *').fill('E2E Graph Theory');
  await page.getByRole('dialog').getByLabel('Description').fill('Created in browser test');
  await page.getByRole('dialog').getByRole('button', { name: 'Create Classroom' }).click();
  await expect(page.getByRole('heading', { name: 'E2E Graph Theory' })).toBeVisible();
});

test('4.3 student can submit a join request in prototype', async ({ page }) => {
  await signIn(page, 'http://127.0.0.1:3100', 'learner@example.test');
  await page.goto('http://127.0.0.1:3100/student/classrooms');
  await expect(page.getByRole('heading', { name: /My Classrooms/i })).toBeVisible();
  await page.getByRole('button', { name: 'Join Class', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  for (const [index, character] of [...'CPLAB26X'].entries()) {
    await page.getByRole('textbox', { name: `Character ${index + 1} of 8` }).fill(character);
  }
  await page.getByRole('dialog').getByRole('button', { name: 'Join Class' }).click();
  await expect(page.getByText('Join Request Submitted', { exact: true })).toBeVisible();
});

test('4.3 uses API data and sends create request when available', async ({ page }) => {
  const base = 'http://127.0.0.1:3101';
  const classroom = {
    id: '11111111-1111-1111-1111-111111111111', name: 'API classroom', description: 'Backend fixture',
    status: 'ACTIVE', classCode: 'ABCDEFGH', codeExpiresAt: null, codeLocked: false,
    joiningOpen: true, joinMode: 'AUTO_JOIN', ownerId: '22222222-2222-2222-2222-222222222222',
    createdAt: '2026-11-09T00:00:00Z', updatedAt: '2026-11-09T00:00:00Z',
  };
  const calls: string[] = [];
  await page.route('**/api/v1/classrooms**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    calls.push(`${request.method()} ${url.pathname}`);
    if (request.method() === 'GET' && url.pathname === '/api/v1/classrooms') {
      return route.fulfill({ json: { success: true, data: { items: [classroom], pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1, isFirst: true, isLast: true } }, timestamp: '2026-11-09T00:00:00Z' } });
    }
    if (request.method() === 'POST' && url.pathname === '/api/v1/classrooms') {
      return route.fulfill({ status: 201, json: { success: true, data: { ...classroom, name: 'Created via API' }, timestamp: '2026-11-09T00:00:00Z' } });
    }
    if (request.method() === 'GET' && url.pathname.endsWith(`/${classroom.id}`)) {
      return route.fulfill({ json: { success: true, data: { ...classroom, name: 'Created via API' }, timestamp: '2026-11-09T00:00:00Z' } });
    }
    if (request.method() === 'GET' && (url.pathname.endsWith('/members') || url.pathname.endsWith('/join-requests'))) {
      return route.fulfill({ json: { success: true, data: { items: [], pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0, isFirst: true, isLast: true } }, timestamp: '2026-11-09T00:00:00Z' } });
    }
    return route.fulfill({ status: 404, json: { success: false, errorCode: 'NOT_FOUND', message: 'Not found', path: url.pathname, timestamp: '2026-11-09T00:00:00Z' } });
  });
  await signIn(page, base, 'teacher');
  await page.goto(`${base}/teacher/classrooms`);
  await expect(page.getByRole('heading', { name: 'API classroom' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Classrooms & Teaching Spaces' })).toHaveCount(0);
  await page.getByRole('textbox', { name: 'Classroom name' }).fill('Created via API');
  await page.getByRole('button', { name: 'Create', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/teacher/classrooms/${classroom.id}$`));
  await expect(page.getByRole('heading', { name: 'Created via API' })).toBeVisible();
  expect(calls).toContain('POST /api/v1/classrooms');
});

test('4.3 teacher can manage classroom detail through API', async ({ page }) => {
  const base = 'http://127.0.0.1:3101';
  const id = '11111111-1111-1111-1111-111111111111';
  const requestId = '22222222-2222-2222-2222-222222222222';
  const invitationId = '33333333-3333-3333-3333-333333333333';
  const calls: string[] = [];
  let status = 'ACTIVE';
  const classroom = () => ({ id, name: 'Managed classroom', description: 'Teacher view', status, classCode: 'ABCDEFGH', codeExpiresAt: null, codeLocked: false, joiningOpen: true, joinMode: 'AUTO_JOIN', ownerId: '44444444-4444-4444-4444-444444444444', createdAt: '2026-11-09T00:00:00Z', updatedAt: '2026-11-09T00:00:00Z' });
  const pageData = (items: unknown[]) => ({ items, pagination: { page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0, isFirst: true, isLast: true } });
  await page.route('**/api/v1/classrooms**', route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    calls.push(`${request.method()} ${path}`);
    if (request.method() === 'GET') {
      const data = path.endsWith('/members') ? pageData([])
        : path.endsWith('/join-requests') ? pageData([{ id: requestId, studentId: '55555555-5555-5555-5555-555555555555', username: 'waiting-student', createdAt: '2026-11-09T00:00:00Z' }])
          : path === '/api/v1/classrooms' ? pageData([classroom()]) : classroom();
      return route.fulfill({ json: { success: true, data, timestamp: '2026-11-09T00:00:00Z' } });
    }
    if (path.endsWith('/archive')) status = 'ARCHIVED';
    if (path.endsWith('/invitations') && request.method() === 'POST') {
      return route.fulfill({ status: 201, json: { success: true, data: invitationId, timestamp: '2026-11-09T00:00:00Z' } });
    }
    if (request.method() === 'PATCH' || path.endsWith('/regenerate')) {
      return route.fulfill({ json: { success: true, data: classroom(), timestamp: '2026-11-09T00:00:00Z' } });
    }
    return route.fulfill({ status: 204, body: '' });
  });
  await signIn(page, base, 'teacher');
  await page.goto(`${base}/teacher/classrooms/${id}`);
  await expect(page.getByRole('heading', { name: 'Managed classroom' })).toBeVisible();
  const edit = page.locator('section').filter({ has: page.getByRole('heading', { name: 'Edit classroom' }) });
  await edit.getByRole('textbox', { name: 'Classroom name' }).fill('Managed classroom');
  await edit.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Classroom updated.');
  await page.getByRole('button', { name: 'Regenerate code' }).click();
  await expect(page.getByRole('status')).toContainText('Class code regenerated.');
  const members = page.locator('section').filter({ has: page.getByRole('heading', { name: 'Members' }) });
  await members.getByRole('textbox', { name: 'Student username or email' }).fill('student@example.test');
  await members.getByRole('button', { name: 'Add' }).click();
  await expect(page.getByRole('status')).toContainText('Student added.');
  await page.getByRole('button', { name: 'Approve' }).click();
  await expect(page.getByRole('status')).toContainText('Join request approved.');
  const invite = page.locator('section').filter({ has: page.getByRole('heading', { name: 'Invite student' }) });
  await invite.getByRole('textbox', { name: 'Student email' }).fill('student@example.test');
  await invite.locator('input[type="datetime-local"]').fill('2026-11-30T09:00');
  await invite.getByRole('button', { name: 'Invite' }).click();
  await expect(page.getByRole('status')).toContainText(invitationId);
  await page.getByRole('button', { name: 'Archive classroom' }).click();
  await expect(page.getByRole('status')).toContainText('Classroom archived.');
  for (const expected of [
    `PATCH /api/v1/classrooms/${id}`,
    `POST /api/v1/classrooms/${id}/class-code/regenerate`,
    `POST /api/v1/classrooms/${id}/members/direct`,
    `POST /api/v1/classrooms/${id}/join-requests/${requestId}/approve`,
    `POST /api/v1/classrooms/${id}/invitations`,
    `POST /api/v1/classrooms/${id}/archive`,
  ]) expect(calls).toContain(expected);
});

test('4.3 falls back to prototype when classroom API is absent', async ({ page }) => {
  const base = 'http://127.0.0.1:3101';
  await page.route('**/api/v1/classrooms**', route => route.fulfill({ status: 404, body: '' }));
  await signIn(page, base, 'teacher');
  await page.goto(`${base}/teacher/classrooms`);
  await expect(page.getByRole('heading', { name: 'Classrooms & Teaching Spaces' })).toBeVisible();
});

test('4.3 student sends class code to API and sees pending approval', async ({ page }) => {
  const base = 'http://127.0.0.1:3101';
  let submittedCode = '';
  await page.route('**/api/v1/classrooms**', async route => {
    const request = route.request();
    if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/v1/classrooms/join') {
      submittedCode = request.postDataJSON().classCode;
      return route.fulfill({ json: { success: true, data: { outcome: 'PENDING_APPROVAL', classroomId: '11111111-1111-1111-1111-111111111111', requestId: '22222222-2222-2222-2222-222222222222' }, timestamp: '2026-11-09T00:00:00Z' } });
    }
    return route.fulfill({ json: { success: true, data: { items: [], pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0, isFirst: true, isLast: true } }, timestamp: '2026-11-09T00:00:00Z' } });
  });
  await signIn(page, base, 'student');
  await page.goto(`${base}/student/classrooms`);
  await expect(page.getByRole('heading', { name: 'Join with class code' })).toBeVisible();
  await page.getByRole('textbox', { name: 'Class code' }).fill('CPLAB26X');
  await page.getByRole('button', { name: 'Join', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Request sent for teacher approval.');
  expect(submittedCode).toBe('CPLAB26X');
});

test('4.3 does not hide authorization errors with mock data', async ({ page }) => {
  const base = 'http://127.0.0.1:3101';
  await page.route('**/api/v1/classrooms**', route => route.fulfill({ status: 403, json: { success: false, errorCode: 'FORBIDDEN', message: 'Access denied', path: '/api/v1/classrooms', timestamp: '2026-11-09T00:00:00Z' } }));
  await signIn(page, base, 'teacher');
  await page.goto(`${base}/teacher/classrooms`);
  await expect(page.getByRole('alert')).toContainText('Access denied');
  await expect(page.getByRole('heading', { name: 'Classrooms & Teaching Spaces' })).toHaveCount(0);
});

test('4.3 does not reuse a previous account classroom list after sign out', async ({ page }) => {
  const base = 'http://127.0.0.1:3101';
  await page.route('**/api/v1/classrooms**', route => {
    const teacher = route.request().headers().authorization === 'Bearer mock-teacher';
    const name = teacher ? 'Teacher private classroom' : 'Student enrolled classroom';
    return route.fulfill({ json: {
      success: true,
      data: {
        items: [{ id: teacher ? '11111111-1111-1111-1111-111111111111' : '22222222-2222-2222-2222-222222222222', name, description: '', status: 'ACTIVE', classCode: null, codeExpiresAt: null, codeLocked: false, joiningOpen: true, joinMode: 'AUTO_JOIN', ownerId: '33333333-3333-3333-3333-333333333333', createdAt: '2026-11-09T00:00:00Z', updatedAt: '2026-11-09T00:00:00Z' }],
        pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1, isFirst: true, isLast: true },
      },
      timestamp: '2026-11-09T00:00:00Z',
    } });
  });
  await signIn(page, base, 'teacher');
  await page.goto(`${base}/teacher/classrooms`);
  await expect(page.getByRole('heading', { name: 'Teacher private classroom' })).toBeVisible();
  await page.getByRole('link', { name: 'Dashboard' }).click();
  await page.getByLabel('Account menu').click();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await signIn(page, base, 'student');
  await page.evaluate(() => {
    window.history.pushState({}, '', '/student/classrooms');
    window.dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page.getByRole('heading', { name: 'Student enrolled classroom' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Teacher private classroom' })).toHaveCount(0);
});

test('4.2 does not reuse another teacher profile after sign out', async ({ page }) => {
  const base = 'http://127.0.0.1:3101';
  await page.route('**/api/v1/auth/login', route => {
    const username = route.request().postDataJSON().usernameOrEmail;
    return route.fulfill({ json: { success: true, data: {
      token: `mock-${username}`,
      user: { id: username, username, email: `${username}@example.test`, role: 'TEACHER',
        fullName: username, lifecycleStatus: 'ACTIVE' },
    }, timestamp: '2026-11-09T00:00:00Z' } });
  });
  await page.route('**/api/v1/users/me/profile', route => {
    const teacher = route.request().headers().authorization === 'Bearer mock-teacher';
    const username = teacher ? 'teacher' : 'teacher2';
    return route.fulfill({ json: { success: true, data: {
      fullName: teacher ? 'Private teacher profile' : 'Second teacher profile', username,
      email: `${username}@example.test`, avatarUrl: null, shortBio: '', role: 'TEACHER',
      joinedAt: '2026-10-01T00:00:00Z', isPublic: false,
      publicPracticeStatistics: { solved: 0 }, streak: { currentStreak: 0, highestStreak: 0 },
    }, timestamp: '2026-11-09T00:00:00Z' } });
  });
  await signIn(page, base, 'teacher');
  await page.goto(`${base}/profile`);
  await expect(page.getByRole('textbox', { name: 'Display name' })).toHaveValue('Private teacher profile');
  await page.getByRole('link', { name: 'Workspace' }).click();
  await page.getByLabel('Account menu').click();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await signIn(page, base, 'teacher2');
  await page.getByRole('link', { name: 'My profile', exact: true }).first().click();
  await expect(page.getByRole('textbox', { name: 'Display name' })).toHaveValue('Second teacher profile');
});

test('4.3 unauthorized API response ends the active session', async ({ page }) => {
  const base = 'http://127.0.0.1:3101';
  await page.route('**/api/v1/classrooms**', route => route.fulfill({ status: 401, json: {
    success: false, errorCode: 'UNAUTHORIZED_ACCESS', message: 'Session expired',
    path: '/api/v1/classrooms', timestamp: '2026-11-09T00:00:00Z',
  } }));
  await signIn(page, base, 'teacher');
  await page.goto(`${base}/teacher/classrooms`);
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => localStorage.getItem('algolearner_token'))).toBeNull();
});
