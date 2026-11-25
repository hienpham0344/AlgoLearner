import { expect, test } from '@playwright/test';
import { installBackendFixture } from './backendFixture';

const base = 'http://127.0.0.1:3101';
const user = {
  id: '11111111-1111-1111-1111-111111111111', username: 'newlearner',
  email: 'newlearner@example.test', role: 'STUDENT', lifecycleStatus: 'ACTIVE',
};
const envelope = (data: object) => ({ success: true, data, timestamp: '2026-11-10T00:00:00Z' });

test('4.1 registers with backend DTO, verifies OTP and restores the session via /auth/me', async ({ page }) => {
  await installBackendFixture(page);
  let registerBody: Record<string, unknown> | undefined;
  let verifyBody: Record<string, unknown> | undefined;
  let meCalls = 0;
  await page.route('**/api/v1/auth/register', async route => {
    registerBody = route.request().postDataJSON();
    await route.fulfill({ status: 201, json: envelope({ email: user.email, message: 'Verification code sent' }) });
  });
  await page.route('**/api/v1/auth/verify-otp', async route => {
    verifyBody = route.request().postDataJSON();
    await route.fulfill({ headers: { 'set-cookie': 'algolearner_refresh=opaque-refresh; Path=/api/v1/auth; HttpOnly; SameSite=Lax' },
      json: envelope({ token: 'signed-access', user }) });
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
  expect(registerBody).toEqual({ email: user.email, username: user.username, password: 'ExamplePass123!',
    confirmPassword: 'ExamplePass123!', role: 'STUDENT', termsAccepted: true, privacyAccepted: true });

  for (const [index, digit] of [...'123456'].entries()) await page.getByLabel(`Digit ${index + 1}`).fill(digit);
  await page.getByRole('button', { name: 'Verify code' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  expect(verifyBody).toEqual({ email: user.email, code: '123456', purpose: 'REGISTER' });
  expect(await page.evaluate(() => localStorage.getItem('algolearner_refresh_token'))).toBeNull();
  expect((await page.context().cookies()).some(cookie => cookie.name === 'algolearner_refresh')).toBe(true);

  await page.reload();
  await expect.poll(() => meCalls).toBeGreaterThan(0);
  await expect(page).toHaveURL(/\/dashboard$/);
});

test('4.1 renews expired access token once and retries /auth/me', async ({ page }) => {
  await installBackendFixture(page);
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
  await page.addInitScript(value => {
    localStorage.setItem('algolearner_token', 'expired-access');
    localStorage.setItem('algolearner_user', JSON.stringify(value));
  }, user);
  await page.context().addCookies([{ name: 'algolearner_refresh', value: 'opaque-refresh', domain: '127.0.0.1', path: '/api/v1/auth' }]);
  await page.route('**/api/v1/auth/refresh-token', async route => {
    refreshCalls++;
    expect(route.request().postData()).toBeNull();
    expect(route.request().headers().cookie).toContain('algolearner_refresh=opaque-refresh');
    await route.fulfill({ json: envelope({ token: 'renewed-access', user }) });
  });
  await page.goto(`${base}/dashboard`);
  await expect.poll(() => page.evaluate(() => localStorage.getItem('algolearner_token'))).toBe('renewed-access');
  expect(refreshCalls).toBe(1);
  await expect(page).toHaveURL(/\/dashboard$/);
});

test('4.1 account security uses session and password endpoints', async ({ page }) => {
  let passwordBody: Record<string, unknown> | undefined;
  let revokedId = '';
  await installBackendFixture(page);
  await page.route('**/api/v1/auth/sessions**', route => {
    if (route.request().method() === 'DELETE') {
      revokedId = new URL(route.request().url()).pathname.split('/').pop() || '';
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ headers: { 'x-algolearner-source': 'backend' }, json: envelope([
      { id: '11111111-1111-1111-1111-111111111112', userAgent: 'Current browser', current: true,
        createdAt: '2026-11-10T00:00:00Z', expiresAt: '2026-12-10T00:00:00Z' },
      ...(revokedId ? [] : [{ id: '11111111-1111-1111-1111-111111111113', userAgent: 'Other browser', current: false,
        createdAt: '2026-11-09T00:00:00Z', expiresAt: '2026-12-09T00:00:00Z' }]),
    ]) });
  });
  await page.route('**/api/v1/auth/change-password', async route => {
    passwordBody = route.request().postDataJSON();
    await route.fulfill({ json: envelope({ success: true, message: 'Password updated' }) });
  });
  await page.addInitScript(value => {
    localStorage.setItem('algolearner_token', 'fixture-student');
    localStorage.setItem('algolearner_user', JSON.stringify(value));
  }, user);
  await page.goto(`${base}/account/security`);
  await expect(page).toHaveURL(/\/profile\?tab=security$/);
  await expect(page.getByRole('tab', { name: 'Security' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByText('Other browser')).toBeVisible();
  await page.getByLabel('Current password').fill('OldPass123');
  await page.getByLabel('New password', { exact: true }).fill('NewPass123');
  await page.getByLabel('Confirm new password').fill('NewPass123');
  await page.getByRole('button', { name: 'Change password' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Password updated' })).toBeVisible();
  expect(passwordBody).toEqual({ currentPassword: 'OldPass123', newPassword: 'NewPass123', confirmPassword: 'NewPass123' });
  await page.getByRole('button', { name: 'Sign out this device' }).click();
  await expect.poll(() => revokedId).toBe('11111111-1111-1111-1111-111111111113');
});

test('4.1 reset password sends the verified grant and confirmed password', async ({ page }) => {
  let resetBody: Record<string, unknown> | undefined;
  let forgotCalls = 0;
  const identity = { usernameOrEmail: user.email, identityType: 'EMAIL' };
  await page.route('**/api/v1/auth/forgot-password', route => {
    forgotCalls++;
    if (forgotCalls === 1) {
      expect(route.request().postDataJSON()).toEqual({ usernameOrEmail: user.email, identityType: 'AUTO' });
      return route.fulfill({ status: 409, json: { success: false, errorCode: 'AMBIGUOUS_IDENTITY',
        message: 'Choose email or username to identify the account.', path: '/api/v1/auth/forgot-password',
        timestamp: '2026-11-10T00:00:00Z' } });
    }
    expect(route.request().postDataJSON()).toEqual(identity);
    return route.fulfill({ json: envelope({ message: 'Sent' }) });
  });
  await page.route('**/api/v1/auth/verify-otp', route => {
    expect(route.request().postDataJSON()).toEqual({ ...identity, code: '123456', purpose: 'RESET_PASSWORD' });
    return route.fulfill({ json: envelope({ resetToken: 'one-use-grant' }) });
  });
  await page.route('**/api/v1/auth/reset-password', async route => {
    resetBody = route.request().postDataJSON();
    await route.fulfill({ json: envelope({ message: 'Updated' }) });
  });
  await page.goto(`${base}/forgot-password`);
  await expect(page.getByLabel('Identify account by')).toHaveCount(0);
  await page.getByLabel('Username or email').fill(user.email);
  await page.getByRole('button', { name: 'Send reset code' }).click();
  await expect(page.getByLabel('Identify account by')).toBeVisible();
  await page.getByLabel('Identify account by').selectOption('EMAIL');
  await page.getByRole('button', { name: 'Send reset code' }).click();
  await expect(page).toHaveURL(/\/verify-otp\?/);
  for (const [index, digit] of [...'123456'].entries()) await page.getByLabel(`Digit ${index + 1}`).fill(digit);
  await page.getByRole('button', { name: 'Verify code' }).click();
  await expect(page).toHaveURL(/\/reset-password$/);
  await page.getByLabel('New password', { exact: true }).fill('NewPass123');
  await page.getByLabel('Confirm password').fill('NewPass123');
  await page.getByRole('button', { name: 'Update password' }).click();
  await expect(page).toHaveURL(/\/login\?resetSuccess=true/);
  expect(resetBody).toEqual({ ...identity, resetToken: 'one-use-grant', newPassword: 'NewPass123', confirmPassword: 'NewPass123' });
});

test('4.1 login hides identity selector by default and reveals it on AMBIGUOUS_IDENTITY', async ({ page }) => {
  await installBackendFixture(page);
  let loginCalls = 0;
  await page.route('**/api/v1/auth/login', route => {
    loginCalls++;
    if (loginCalls === 1) {
      expect(route.request().postDataJSON()).toMatchObject({ usernameOrEmail: user.email, identityType: 'AUTO' });
      return route.fulfill({ status: 409, json: { success: false, errorCode: 'AMBIGUOUS_IDENTITY',
        message: 'Choose email or username to identify the account.', path: '/api/v1/auth/login',
        timestamp: '2026-11-10T00:00:00Z' } });
    }
    expect(route.request().postDataJSON()).toMatchObject({ usernameOrEmail: user.email, identityType: 'EMAIL' });
    return route.fulfill({ json: envelope({ token: 'signed-access', user }) });
  });
  await page.goto(`${base}/login`);
  await expect(page.getByLabel('Identify account by')).toHaveCount(0);
  await page.getByLabel('Username or email').fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill('ExamplePass123!');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByLabel('Identify account by')).toBeVisible();
  await page.getByLabel('Identify account by').selectOption('EMAIL');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'You’re all set.' })).toBeVisible();
});

test('4.1 sign out revokes the current backend session', async ({ page }) => {
  await installBackendFixture(page);
  let logoutCalled = false;
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: envelope(user) }));
  await page.route('**/api/v1/auth/logout', async route => {
    expect(route.request().headers().authorization).toBe('Bearer signed-access');
    expect(route.request().headers().cookie).toContain('algolearner_refresh=opaque-refresh');
    expect(route.request().headers()['x-refresh-token']).toBeUndefined();
    logoutCalled = true;
    await route.fulfill({ headers: { 'set-cookie': 'algolearner_refresh=; Path=/api/v1/auth; Max-Age=0; HttpOnly' },
      json: envelope({ message: 'Logged out' }) });
  });
  await page.addInitScript(value => {
    localStorage.setItem('algolearner_token', 'signed-access');
    localStorage.setItem('algolearner_user', JSON.stringify(value));
  }, user);
  await page.context().addCookies([{ name: 'algolearner_refresh', value: 'opaque-refresh', domain: '127.0.0.1', path: '/api/v1/auth' }]);
  await page.goto(`${base}/dashboard`);
  await page.getByLabel('Account menu').click();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect.poll(() => logoutCalled).toBe(true);
  expect(await page.evaluate(() => localStorage.getItem('algolearner_token'))).toBeNull();
  expect((await page.context().cookies()).some(cookie => cookie.name === 'algolearner_refresh')).toBe(false);
});

test('4.1 sign out uses the refresh cookie if the bearer token has expired', async ({ page }) => {
  await installBackendFixture(page);
  let logoutCalls = 0;
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: envelope(user) }));
  await page.route('**/api/v1/auth/logout', async route => {
    logoutCalls++;
    if (logoutCalls === 2) expect(route.request().headers().authorization).toBeUndefined();
    await route.fulfill(logoutCalls === 1
      ? { status: 401, json: { success: false, errorCode: 'UNAUTHORIZED', message: 'Expired', path: '/api/v1/auth/logout', timestamp: '2026-11-10T00:00:00Z' } }
      : { headers: { 'set-cookie': 'algolearner_refresh=; Path=/api/v1/auth; Max-Age=0; HttpOnly' },
        json: envelope({ message: 'Logged out' }) });
  });
  await page.addInitScript(value => {
    localStorage.setItem('algolearner_token', 'expired-access');
    localStorage.setItem('algolearner_user', JSON.stringify(value));
  }, user);
  await page.context().addCookies([{ name: 'algolearner_refresh', value: 'opaque-refresh', domain: '127.0.0.1', path: '/api/v1/auth' }]);
  await page.goto(`${base}/dashboard`);
  await page.getByLabel('Account menu').click();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect.poll(() => logoutCalls).toBe(2);
});
