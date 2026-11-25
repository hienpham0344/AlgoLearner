import { expect, test, type Page } from '@playwright/test';
import { installBackendFixture } from './backendFixture';

const base = 'http://127.0.0.1:3101';
const timestamp = '2026-11-12T00:00:00Z';
const pageData = (items: object[]) => ({ items, pagination: {
  page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0,
  isFirst: true, isLast: true,
} });
const success = (data: object) => ({ success: true, data, timestamp });

async function login(page: Page, identity: string) {
  await page.goto(`${base}/login`);
  await page.getByLabel('Username or email').fill(identity);
  await page.getByLabel('Password', { exact: true }).fill('ExamplePass123!');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.getByRole('button', { name: 'Continue to your workspace' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

test('Admin can suspend an account through the backend contract', async ({ page }) => {
  await installBackendFixture(page);
  let suspended = false;
  await page.route('**/api/v1/admin/accounts**', route => {
    const path = new URL(route.request().url()).pathname;
    const account = { id: 'student-1', username: 'student1', email: 'student1@example.test', role: 'STUDENT',
      status: suspended ? 'SUSPENDED' : 'ACTIVE', suspensionReason: suspended ? 'Policy violation' : null,
      suspensionEndsAt: null, createdAt: timestamp };
    if (path === '/api/v1/admin/accounts') return route.fulfill({ json: success(pageData([account])) });
    if (path === '/api/v1/admin/accounts/student-1/suspend') {
      expect(route.request().postDataJSON()).toEqual({ reason: 'Policy violation', endsAt: null });
      suspended = true;
      return route.fulfill({ json: success({ ...account, status: 'SUSPENDED', suspensionReason: 'Policy violation' }) });
    }
    return route.fulfill({ status: 404 });
  });
  await login(page, 'admin@example.test');
  await page.goto(`${base}/admin/accounts`);
  await page.getByLabel('Lý do đình chỉ').fill('Policy violation');
  await page.getByRole('button', { name: 'Đình chỉ' }).click();
  await expect(page.getByText('Lý do đình chỉ: Policy violation')).toBeVisible();
});

test('Student revokes another login session from Security', async ({ page }) => {
  await installBackendFixture(page);
  let revoked = false;
  await page.route('**/api/v1/auth/sessions**', route => {
    if (route.request().method() === 'DELETE') {
      expect(new URL(route.request().url()).pathname).toBe('/api/v1/auth/sessions/other-1');
      revoked = true;
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ json: success([
      { id: 'current-1', createdAt: timestamp, expiresAt: '2026-12-01T00:00:00Z', rememberMe: false,
        current: true, userAgent: 'Current browser' },
      ...(!revoked ? [{ id: 'other-1', createdAt: timestamp, expiresAt: '2026-12-01T00:00:00Z',
        rememberMe: true, current: false, userAgent: 'Other browser' }] : []),
    ]) });
  });
  await login(page, 'student@example.test');
  await page.goto(`${base}/profile`);
  await page.getByRole('tab', { name: 'Security' }).click();
  await expect(page.getByText('Other browser')).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất thiết bị này' }).click();
  await expect(page.getByText('Other browser')).toHaveCount(0);
  await expect(page.getByText('Thiết bị hiện tại')).toBeVisible();
});

test('Teacher revokes a listed invitation without entering a UUID', async ({ page }) => {
  await installBackendFixture(page);
  let revoked = false;
  const classroom = { id: 'class-1', name: 'Algorithms', description: '', status: 'ACTIVE', classCode: 'ABCD1234',
    codeExpiresAt: null, codeLocked: false, joiningOpen: true, joinMode: 'AUTO_JOIN', ownerId: 'test-teacher',
    createdAt: timestamp, updatedAt: timestamp };
  await page.route('**/api/v1/classrooms**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/classrooms') return route.fulfill({ json: success(pageData([classroom])) });
    if (path === '/api/v1/classrooms/class-1') return route.fulfill({ json: success(classroom) });
    if (path.endsWith('/members') || path.endsWith('/join-requests')) return route.fulfill({ json: success(pageData([])) });
    if (path.endsWith('/invitations') && route.request().method() === 'GET') return route.fulfill({ json: success(pageData([{
      id: 'invite-1', classroomId: 'class-1', classroomName: 'Algorithms', studentId: 'student-1',
      studentEmail: 'student@example.test', status: revoked ? 'REVOKED' : 'PENDING', expiresAt: '2026-12-01T00:00:00Z', createdAt: timestamp,
    }])) });
    if (path.endsWith('/invitations/invite-1/revoke')) { revoked = true; return route.fulfill({ status: 204 }); }
    return route.fulfill({ status: 404 });
  });
  await login(page, 'teacher@example.test');
  await page.goto(`${base}/teacher/classrooms/class-1`);
  await page.getByRole('tab', { name: /members/i }).click();
  await expect(page.getByText('student@example.test · PENDING')).toBeVisible();
  await page.getByRole('button', { name: 'Thu hồi' }).click();
  await expect(page.getByText('student@example.test · REVOKED')).toBeVisible();
});

test('Student accepts a notification and reaches the classroom', async ({ page }) => {
  await installBackendFixture(page);
  let accepted = false;
  await page.route('**/api/v1/classrooms**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/classrooms') return route.fulfill({ json: success(pageData(accepted ? [{
      id: 'class-1', name: 'Algorithms', description: '', status: 'ACTIVE', classCode: null,
      codeExpiresAt: null, codeLocked: false, joiningOpen: false, joinMode: 'AUTO_JOIN',
      ownerId: 'teacher-1', createdAt: timestamp, updatedAt: timestamp,
    }] : [])) });
    if (path === '/api/v1/classrooms/invitations/invite-1/accept') {
      accepted = true; return route.fulfill({ status: 204 });
    }
    if (path === '/api/v1/classrooms/class-1') return route.fulfill({ json: success({ id: 'class-1', name: 'Algorithms',
      description: '', status: 'ACTIVE', classCode: null, codeExpiresAt: null, codeLocked: false,
      joiningOpen: false, joinMode: 'AUTO_JOIN', ownerId: 'teacher-1', createdAt: timestamp, updatedAt: timestamp }) });
    return route.fulfill({ status: 404 });
  });
  await page.route('**/api/v1/notifications**', route => route.fulfill({ json: success(pageData([{
    id: 'notification-1', invitationId: 'invite-1', classroomId: 'class-1', classroomName: 'Algorithms',
    status: accepted ? 'ACCEPTED' : 'PENDING', expiresAt: '2026-12-01T00:00:00Z', createdAt: timestamp, readAt: null,
  }])) }));
  await login(page, 'student@example.test');
  await page.goto(`${base}/student/classrooms`);
  await expect(page.getByText('Algorithms', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Chấp nhận' }).click();
  await expect(page).toHaveURL(/\/student\/classrooms\/class-1$/);
});
