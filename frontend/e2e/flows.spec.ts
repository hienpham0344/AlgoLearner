import { installBackendFixture } from './backendFixture';
import { expect, test, type Page } from '@playwright/test';
import { writeFile } from 'node:fs/promises';

test.beforeEach(async ({ page }, testInfo) => {
  if (/^(4\.3|4\.2 profile reads|4\.2 does not)/.test(testInfo.title) && !/in prototype|classroom prototype/.test(testInfo.title)) await installBackendFixture(page);
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

test('registration validates password rules before calling the backend', async ({ page }) => {
  let requests = 0;
  await page.route('**/api/v1/auth/register', route => {
    requests += 1;
    return route.fulfill({ status: 503, body: 'Service unavailable' });
  });
  await page.goto('http://127.0.0.1:3101/register');
  await page.getByLabel('Email address').fill('new-user@example.test');
  await page.getByLabel('Username').fill('new-user');
  await page.getByLabel(/I agree to the Terms of Service/).check();
  await page.getByLabel(/I accept the Privacy Policy/).check();
  for (const password of ['12345678', 'abcdefgh']) {
    await page.getByLabel('Password', { exact: true }).fill(password);
    await page.getByLabel('Confirm password').fill(password);
    await page.getByRole('button', { name: 'Create account' }).click();
    await expect(page.locator('#reg-password-message')).toHaveText('Use at least one letter and one number.');
    await expect(page.getByLabel('Password', { exact: true })).toBeFocused();
    expect(requests).toBe(0);
  }
  const longPassword = 'a1' + 'é'.repeat(36);
  await page.getByLabel('Password', { exact: true }).fill(longPassword);
  await page.getByLabel('Confirm password').fill(longPassword);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.locator('#reg-password-message')).toHaveText('Password is too long. Use at most 72 UTF-8 bytes.');
  expect(requests).toBe(0);
  await page.getByLabel('Password', { exact: true }).fill('example123');
  await page.getByLabel('Confirm password').fill('example123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('alert')).toContainText('We couldn’t create your account. Please try again.');
  expect(requests).toBe(1);
});

test('registration explains unavailable account creation in backend preview', async ({ page }) => {
  await page.route('**/api/v1/auth/register', route => route.fulfill({
    status: 401,
    json: {
      success: false, errorCode: 'UNAUTHORIZED_ACCESS', message: 'Authentication is required',
      path: '/api/v1/auth/register', timestamp: '2026-11-10T00:00:00Z',
    },
  }));
  await page.goto('http://127.0.0.1:3101/register');
  await page.getByLabel('Email address').fill('new-user@example.test');
  await page.getByLabel('Username').fill('new-user');
  await page.getByLabel('Password', { exact: true }).fill('ExamplePass123!');
  await page.getByLabel('Confirm password').fill('ExamplePass123!');
  await page.getByLabel(/I agree to the Terms of Service/).check();
  await page.getByLabel(/I accept the Privacy Policy/).check();
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('alert')).toContainText('Account creation isn’t available in this preview yet.');
  await expect(page).toHaveURL(/\/register$/);
});

test('registration stays on the form when the backend cannot send OTP', async ({ page }) => {
  await page.route('**/api/v1/auth/register', route => route.fulfill({ status: 503, body: 'Service unavailable' }));
  await page.goto('http://127.0.0.1:3101/register');
  await page.getByLabel('Email address').fill('new-user@example.test');
  await page.getByLabel('Username').fill('new-user');
  await page.getByLabel('Password', { exact: true }).fill('ExamplePass123!');
  await page.getByLabel('Confirm password').fill('ExamplePass123!');
  await page.getByLabel(/I agree to the Terms of Service/).check();
  await page.getByLabel(/I accept the Privacy Policy/).check();
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('alert')).toContainText('We couldn’t create your account. Please try again.');
  await expect(page).toHaveURL(/\/register$/);
});

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
  await expect(page.locator('.folder-feedback-toast')).toContainText('Your public profile is visible.');
});

for (const username of ['learner', 'teacher']) {
  test(`4.2 ${username} account menu opens profile and matching settings tabs`, async ({ page }) => {
    await signIn(page, 'http://127.0.0.1:3100', `${username}@example.test`);
    const menu = page.locator('.dashboard-profile-menu');
    await expect(page.getByLabel('Account menu')).toBeVisible();
    await expect(page.getByRole('link', { name: 'My profile', exact: true })).toHaveCount(0);
    await page.getByLabel('Account menu').click();
    const profileLink = menu.getByRole('link', { name: username, exact: true });
    await expect(profileLink).toBeVisible();
    await profileLink.focus();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/profile$/);
    await expect(page.getByRole('tab', { name: 'Profile', exact: true })).toHaveAttribute('aria-selected', 'true');

    for (const [label, tab] of [['Profile settings', 'profile'], ['Account security', 'security']]) {
      await page.getByRole('link', { name: 'Workspace', exact: true }).click();
      await page.getByLabel('Account menu').click();
      await menu.getByRole('link', { name: label, exact: true }).click();
      await expect(page).toHaveURL(new RegExp(`/profile\\?tab=${tab}$`));
      await expect(page.getByRole('tab', { name: tab === 'profile' ? 'Profile' : 'Security', exact: true }))
        .toHaveAttribute('aria-selected', 'true');
    }
  });
}

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
  await page.getByRole('button', { name: /\+ New classroom/i }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('dialog').getByRole('textbox', { name: 'Classroom name' }).fill('Created via API');
  await page.getByRole('dialog').getByRole('button', { name: 'Create', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/teacher/classrooms/${classroom.id}$`));
  await expect(page.getByRole('heading', { name: 'Created via API' })).toBeVisible();
  expect(calls).toContain('POST /api/v1/classrooms');
});

test('4.3 minimalist table filters, searches, and creates classroom with join policy', async ({ page }) => {
  const base = 'http://127.0.0.1:3101';
  const activeClass = {
    id: '11111111-1111-1111-1111-111111111111', name: 'Algorithms K23', description: 'Graph theory',
    status: 'ACTIVE', classCode: 'ALGO2026', codeExpiresAt: null, codeLocked: false,
    joiningOpen: true, joinMode: 'REQUIRE_APPROVAL', ownerId: '22222222-2222-2222-2222-222222222222',
    createdAt: '2026-11-09T00:00:00Z', updatedAt: '2026-11-09T00:00:00Z',
  };
  const archivedClass = {
    id: '33333333-3333-3333-3333-333333333333', name: 'Legacy Java 101', description: 'Archived term',
    status: 'ARCHIVED', classCode: 'JAVA2025', codeExpiresAt: null, codeLocked: true,
    joiningOpen: false, joinMode: 'AUTO_JOIN', ownerId: '22222222-2222-2222-2222-222222222222',
    createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-15T00:00:00Z',
  };
  let postedBody: Record<string, unknown> | null = null;
  await page.route('**/api/v1/classrooms**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (request.method() === 'GET' && url.pathname === '/api/v1/classrooms') {
      return route.fulfill({ json: { success: true, data: { items: [activeClass, archivedClass], pagination: { page: 0, size: 20, totalElements: 2, totalPages: 1, isFirst: true, isLast: true } }, timestamp: '2026-11-09T00:00:00Z' } });
    }
    if (request.method() === 'POST' && url.pathname === '/api/v1/classrooms') {
      postedBody = request.postDataJSON();
      return route.fulfill({ status: 201, json: { success: true, data: { ...activeClass, id: '44444444-4444-4444-4444-444444444444', name: String(postedBody?.name ?? 'New Class'), joinMode: postedBody?.joinMode ?? 'AUTO_JOIN' }, timestamp: '2026-11-09T00:00:00Z' } });
    }
    if (request.method() === 'GET' && url.pathname.endsWith('/44444444-4444-4444-4444-444444444444')) {
      return route.fulfill({ json: { success: true, data: { ...activeClass, id: '44444444-4444-4444-4444-444444444444', name: 'New Approval Class', joinMode: 'REQUIRE_APPROVAL' }, timestamp: '2026-11-09T00:00:00Z' } });
    }
    if (request.method() === 'GET' && (url.pathname.endsWith('/members') || url.pathname.endsWith('/join-requests'))) {
      return route.fulfill({ json: { success: true, data: { items: [], pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0, isFirst: true, isLast: true } }, timestamp: '2026-11-09T00:00:00Z' } });
    }
    return route.fulfill({ status: 404, json: { success: false, errorCode: 'NOT_FOUND', message: 'Not found', path: url.pathname, timestamp: '2026-11-09T00:00:00Z' } });
  });

  await signIn(page, base, 'teacher');
  await page.goto(`${base}/teacher/classrooms`);
  await expect(page.getByRole('table')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Algorithms K23' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Legacy Java 101' })).toBeVisible();

  await page.getByRole('tab', { name: 'Active (1)' }).click();
  await expect(page.getByRole('heading', { name: 'Algorithms K23' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Legacy Java 101' })).toHaveCount(0);

  await page.getByRole('tab', { name: 'Archived (1)' }).click();
  await expect(page.getByRole('heading', { name: 'Legacy Java 101' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Algorithms K23' })).toHaveCount(0);

  await page.getByRole('tab', { name: 'All Classes (2)' }).click();
  await page.getByRole('textbox', { name: 'Search classrooms' }).fill('ALGO2026');
  await expect(page.getByRole('heading', { name: 'Algorithms K23' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Legacy Java 101' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Clear search' }).click();

  await page.keyboard.press('n');
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('dialog').getByRole('textbox', { name: 'Classroom name' }).fill('New Approval Class');
  await page.getByRole('dialog').locator('label').filter({ hasText: 'Require approval' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Create', exact: true }).click();
  await expect(page).toHaveURL(/\/teacher\/classrooms\/44444444-4444-4444-4444-444444444444$/);
  expect(postedBody).toMatchObject({ name: 'New Approval Class', joinMode: 'REQUIRE_APPROVAL' });
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
          : path.endsWith('/invitations') ? pageData([])
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
  await page.getByRole('tab', { name: /settings/i }).click();
  const edit = page.locator('section').filter({ has: page.getByRole('heading', { name: 'Edit classroom' }) });
  await edit.getByRole('textbox', { name: 'Classroom name' }).fill('Managed classroom');
  await edit.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Classroom updated.');
  await page.getByRole('button', { name: 'Regenerate code' }).click();
  await expect(page.getByRole('status')).toContainText('Class code regenerated.');
  await page.getByRole('tab', { name: /members/i }).click();
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
  await page.getByRole('tab', { name: /settings/i }).click();
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

test('4.3 shows backend 404 without substituting prototype data', async ({ page }) => {
  const base = 'http://127.0.0.1:3101';
  await page.route('**/api/v1/classrooms**', route => route.fulfill({ status: 404, body: '' }));
  await signIn(page, base, 'teacher');
  await page.goto(`${base}/teacher/classrooms`);
  await expect(page.getByRole('alert')).toContainText('HTTP 404');
  await expect(page.getByRole('heading', { name: 'Classrooms & Teaching Spaces' })).toHaveCount(0);
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
  await page.getByRole('button', { name: 'Join Class' }).click();
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
    const teacher = route.request().headers().authorization === 'Bearer fixture-teacher';
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
      token: `fixture-${username}`,
      user: { id: username, username, email: `${username}@example.test`, role: 'TEACHER',
        fullName: username, lifecycleStatus: 'ACTIVE' },
    }, timestamp: '2026-11-09T00:00:00Z' } });
  });
  await page.route('**/api/v1/users/me/profile', route => {
    const teacher = route.request().headers().authorization === 'Bearer fixture-teacher';
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
  await page.getByLabel('Account menu').click();
  await page.locator('.dashboard-profile-menu').getByRole('link', { name: 'teacher2', exact: true }).click();
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
