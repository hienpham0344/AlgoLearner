import { expect, test } from '@playwright/test';

const base = 'http://127.0.0.1:3101';
const user = {
  id: '11111111-1111-1111-1111-111111111111', username: 'newlearner',
  email: 'newlearner@example.test', role: 'STUDENT', lifecycleStatus: 'ACTIVE',
};
const envelope = (data: object) => ({ success: true, data, timestamp: '2026-11-10T00:00:00Z' });

test('4.1 registers with backend DTO, verifies OTP and restores the session via /auth/me', async ({ page }) => {
  let registerBody: Record<string, unknown> | undefined;
  let verifyBody: Record<string, unknown> | undefined;
  let meCalls = 0;
  await page.route('**/api/v1/auth/register', async route => {
    registerBody = route.request().postDataJSON();
    await route.fulfill({ status: 201, json: envelope({ ...user, lifecycleStatus: 'PENDING_VERIFICATION' }) });
  });
  await page.route('**/api/v1/auth/verify-otp', async route => {
    verifyBody = route.request().postDataJSON();
    await route.fulfill({ json: envelope({ token: 'signed-access', refreshToken: 'opaque-refresh', user, expiresIn: 900 }) });
  });
  await page.route('**/api/v1/auth/me', async route => {
    meCalls++;
    expect(route.request().headers().authorization).toBe('Bearer signed-access');
    await route.fulfill({ json: envelope(user) });
  });

  await page.goto(`${base}/register`);
  await page.getByLabel('Email address').fill(user.email);
  await page.getByLabel('Username').fill(user.username);
  await page.getByLabel('Password', { exact: true }).fill('ExamplePass123!');
  await page.getByLabel('Confirm password').fill('ExamplePass123!');
  await page.getByLabel(/I agree to the Terms of Service/).check();
  await page.getByLabel(/I accept the Privacy Policy/).check();
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/\/verify-otp\?/);
  expect(registerBody).toEqual({ email: user.email, username: user.username, password: 'ExamplePass123!', role: 'STUDENT', termsAccepted: true, privacyAccepted: true });

  for (const [index, digit] of [...'123456'].entries()) await page.getByLabel(`Digit ${index + 1}`).fill(digit);
  await page.getByRole('button', { name: 'Verify code' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  expect(verifyBody).toEqual({ email: user.email, code: '123456', purpose: 'REGISTER' });
  expect(await page.evaluate(() => localStorage.getItem('algolearner_refresh_token'))).toBe('opaque-refresh');

  await page.reload();
  await expect.poll(() => meCalls).toBeGreaterThan(0);
  await expect(page).toHaveURL(/\/dashboard$/);
});

test('4.1 renews expired access token once and retries /auth/me', async ({ page }) => {
  let refreshCalls = 0;
  await page.route('**/api/v1/auth/me', async route => {
    const authorization = route.request().headers().authorization;
    if (authorization === 'Bearer expired-access') {
      await route.fulfill({ status: 401, json: { success: false, errorCode: 'UNAUTHORIZED', message: 'Expired', path: '/api/v1/auth/me', timestamp: '2026-11-10T00:00:00Z' } });
    } else {
      expect(authorization).toBe('Bearer renewed-access');
      await route.fulfill({ json: envelope(user) });
    }
  });
  await page.route('**/api/v1/auth/refresh-token', async route => {
    refreshCalls++;
    expect(route.request().postDataJSON()).toEqual({ refreshToken: 'opaque-refresh' });
    await route.fulfill({ json: envelope({ token: 'renewed-access', refreshToken: 'opaque-refresh', user, expiresIn: 900 }) });
  });
  await page.goto(`${base}/login`);
  await page.evaluate(value => {
    localStorage.setItem('algolearner_token', 'expired-access');
    localStorage.setItem('algolearner_refresh_token', 'opaque-refresh');
    localStorage.setItem('algolearner_user', JSON.stringify(value));
  }, user);
  await page.goto(`${base}/dashboard`);
  await expect.poll(() => page.evaluate(() => localStorage.getItem('algolearner_token'))).toBe('renewed-access');
  expect(refreshCalls).toBe(1);
  await expect(page).toHaveURL(/\/dashboard$/);
});

test('4.1 account security uses session and password endpoints', async ({ page }) => {
  let passwordBody: Record<string, unknown> | undefined;
  let revokedId = '';
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: envelope(user) }));
  await page.route('**/api/v1/auth/sessions', route => route.fulfill({ json: envelope([
    { id: '11111111-1111-1111-1111-111111111112', deviceLabel: 'Current browser', current: true, lastSeenAt: '2026-11-10T00:00:00Z' },
    { id: '11111111-1111-1111-1111-111111111113', deviceLabel: 'Other browser', current: false, lastSeenAt: '2026-11-09T00:00:00Z' },
  ]) }));
  await page.route('**/api/v1/auth/change-password', async route => {
    passwordBody = route.request().postDataJSON();
    expect(route.request().headers()['x-refresh-token']).toBe('opaque-refresh');
    await route.fulfill({ json: envelope({ message: 'Password changed' }) });
  });
  await page.route('**/api/v1/auth/sessions/*', async route => {
    revokedId = new URL(route.request().url()).pathname.split('/').pop() || '';
    await route.fulfill({ json: envelope({ message: 'Revoked' }) });
  });
  await page.goto(`${base}/login`);
  await page.evaluate(value => {
    localStorage.setItem('algolearner_token', 'signed-access');
    localStorage.setItem('algolearner_refresh_token', 'opaque-refresh');
    localStorage.setItem('algolearner_user', JSON.stringify(value));
  }, user);
  await page.goto(`${base}/account/security`);
  await expect(page.getByText('Other browser')).toBeVisible();
  await page.getByLabel('Current password').fill('OldPass123');
  await page.getByLabel('New password', { exact: true }).fill('NewPass123');
  await page.getByLabel('Confirm new password').fill('NewPass123');
  await page.getByRole('button', { name: 'Save password' }).click();
  await expect(page.getByRole('status')).toContainText('Password updated');
  expect(passwordBody).toEqual({ currentPassword: 'OldPass123', newPassword: 'NewPass123' });
  await page.getByRole('button', { name: 'Revoke' }).click();
  await expect.poll(() => revokedId).toBe('11111111-1111-1111-1111-111111111113');
});

test('4.1 reset password sends only fields accepted by backend', async ({ page }) => {
  let resetBody: Record<string, unknown> | undefined;
  await page.route('**/api/v1/auth/forgot-password', route => route.fulfill({ json: envelope({ message: 'Sent' }) }));
  await page.route('**/api/v1/auth/verify-otp', route => route.fulfill({ json: envelope({ resetToken: 'one-use-grant' }) }));
  await page.route('**/api/v1/auth/reset-password', async route => {
    resetBody = route.request().postDataJSON();
    await route.fulfill({ json: envelope({ message: 'Updated' }) });
  });
  await page.goto(`${base}/forgot-password`);
  await page.getByLabel('Email address').fill(user.email);
  await page.getByRole('button', { name: /send|reset/i }).click();
  await expect(page).toHaveURL(/\/verify-otp\?/);
  for (const [index, digit] of [...'123456'].entries()) await page.getByLabel(`Digit ${index + 1}`).fill(digit);
  await page.getByRole('button', { name: 'Verify code' }).click();
  await expect(page).toHaveURL(/\/reset-password$/);
  await page.getByLabel('New password', { exact: true }).fill('NewPass123');
  await page.getByLabel('Confirm password').fill('NewPass123');
  await page.getByRole('button', { name: 'Update password' }).click();
  await expect(page).toHaveURL(/\/login\?resetSuccess=true/);
  expect(resetBody).toEqual({ email: user.email, resetToken: 'one-use-grant', newPassword: 'NewPass123' });
});

test('4.1 sign out revokes the current backend session', async ({ page }) => {
  let logoutCalled = false;
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: envelope(user) }));
  await page.route('**/api/v1/auth/logout', async route => {
    expect(route.request().headers().authorization).toBe('Bearer signed-access');
    expect(route.request().headers()['x-refresh-token']).toBe('opaque-refresh');
    logoutCalled = true;
    await route.fulfill({ json: envelope({ message: 'Logged out' }) });
  });
  await page.goto(`${base}/login`);
  await page.evaluate(value => {
    localStorage.setItem('algolearner_token', 'signed-access');
    localStorage.setItem('algolearner_refresh_token', 'opaque-refresh');
    localStorage.setItem('algolearner_user', JSON.stringify(value));
  }, user);
  await page.goto(`${base}/dashboard`);
  await page.getByLabel('Account menu').click();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect.poll(() => logoutCalled).toBe(true);
  expect(await page.evaluate(() => localStorage.getItem('algolearner_refresh_token'))).toBeNull();
});

test('4.1 sign out refreshes an expired access token before revoking the session', async ({ page }) => {
  let logoutCalls = 0;
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: envelope(user) }));
  await page.route('**/api/v1/auth/refresh-token', route => route.fulfill({ json: envelope({ token: 'renewed-access', refreshToken: 'opaque-refresh', user, expiresIn: 900 }) }));
  await page.route('**/api/v1/auth/logout', async route => {
    logoutCalls++;
    await route.fulfill(logoutCalls === 1
      ? { status: 401, json: { success: false, errorCode: 'UNAUTHORIZED', message: 'Expired', path: '/api/v1/auth/logout', timestamp: '2026-11-10T00:00:00Z' } }
      : { json: envelope({ message: 'Logged out' }) });
  });
  await page.goto(`${base}/login`);
  await page.evaluate(value => {
    localStorage.setItem('algolearner_token', 'expired-access');
    localStorage.setItem('algolearner_refresh_token', 'opaque-refresh');
    localStorage.setItem('algolearner_user', JSON.stringify(value));
  }, user);
  await page.goto(`${base}/dashboard`);
  await page.getByLabel('Account menu').click();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect.poll(() => logoutCalls).toBe(2);
});
