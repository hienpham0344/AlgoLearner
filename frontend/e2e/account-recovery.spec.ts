import { expect, test } from '@playwright/test';

const base = 'http://127.0.0.1:3101';
const user = { id: 'test-user', username: 'learner', email: 'learner@example.test', role: 'STUDENT', lifecycleStatus: 'ACTIVE' };

for (const rememberMe of [false, true]) {
  test(`Remember me submits ${rememberMe} from the checkbox`, async ({ page }) => {
    let submitted: boolean | undefined;
    await page.route('**/api/v1/auth/login', route => {
      submitted = route.request().postDataJSON().rememberMe;
      return route.fulfill({ json: { data: { token: 'mock-student', user } } });
    });
    await page.goto(`${base}/login`);
    await page.getByLabel('Username or email').fill(user.email);
    await page.getByLabel('Password', { exact: true }).fill('ExamplePass123!');
    const checkbox = page.getByRole('checkbox', { name: 'Remember me for 30 days' });
    await expect(checkbox).not.toBeChecked();
    if (rememberMe) await checkbox.check();
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'You’re all set.' })).toBeVisible();
    expect(submitted).toBe(rememberMe);
  });
}

test('forgot password verifies a reset grant and validates the new password', async ({ page }) => {
  let resetRequests = 0;
  await page.route('**/api/v1/auth/forgot-password', route => route.fulfill({ json: { data: { message: 'Code sent' } } }));
  await page.route('**/api/v1/auth/verify-otp', route => {
    expect(route.request().postDataJSON().purpose).toBe('RESET_PASSWORD');
    return route.fulfill({ json: { data: { resetToken: 'synthetic-single-use-grant' } } });
  });
  await page.route('**/api/v1/auth/reset-password', route => {
    resetRequests++;
    expect(route.request().postDataJSON()).toEqual({ email: user.email, resetToken: 'synthetic-single-use-grant', newPassword: 'NewPassword123!', confirmPassword: 'NewPassword123!' });
    return route.fulfill({ json: { data: { success: true, message: 'Password updated' } } });
  });
  await page.goto(`${base}/forgot-password`);
  await page.getByLabel('Username or email').fill(user.email);
  await page.getByRole('button', { name: 'Send reset code' }).click();
  await expect(page.getByRole('heading', { name: 'Verify your reset code.' })).toBeVisible();
  for (let digit = 1; digit <= 6; digit++) await page.getByLabel(`Digit ${digit}`, { exact: true }).fill(String(digit));
  await page.getByRole('button', { name: 'Verify code' }).click();
  await expect(page.getByRole('heading', { name: 'Set a new password.' })).toBeVisible();
  await page.getByLabel('New password', { exact: true }).fill('abcdefgh');
  await page.getByLabel('Confirm password', { exact: true }).fill('abcdefgh');
  await page.getByRole('button', { name: 'Update password' }).click();
  await expect(page.getByRole('alert')).toContainText('Use at least one letter and one number.');
  expect(resetRequests).toBe(0);
  await page.getByLabel('New password', { exact: true }).fill('NewPassword123!');
  await page.getByLabel('Confirm password', { exact: true }).fill('NewPassword123!');
  await page.getByRole('button', { name: 'Update password' }).click();
  await expect(page).toHaveURL(/\/login\?resetSuccess=true$/);
  expect(resetRequests).toBe(1);
});

test('forgot password accepts username without exposing the registered email', async ({ page }) => {
  const identity = { usernameOrEmail: 'learner' };
  await page.route('**/api/v1/auth/forgot-password', route => {
    expect(route.request().postDataJSON()).toEqual(identity);
    return route.fulfill({ json: { data: { email: null, message: 'Code sent' } } });
  });
  await page.route('**/api/v1/auth/verify-otp', route => {
    expect(route.request().postDataJSON()).toEqual({ ...identity, code: '123456', purpose: 'RESET_PASSWORD' });
    return route.fulfill({ json: { data: { resetToken: 'grant-1' } } });
  });
  await page.route('**/api/v1/auth/reset-password', route => {
    expect(route.request().postDataJSON()).toEqual({ ...identity, resetToken: 'grant-1',
      newPassword: 'NewPassword123!', confirmPassword: 'NewPassword123!' });
    return route.fulfill({ json: { data: { success: true, message: 'Password updated' } } });
  });
  await page.goto(`${base}/forgot-password`);
  await page.getByLabel('Username or email').fill('learner');
  await page.getByRole('button', { name: 'Send reset code' }).click();
  await expect(page).toHaveURL(/usernameOrEmail=learner/);
  for (let digit = 1; digit <= 6; digit++) await page.getByLabel(`Digit ${digit}`, { exact: true }).fill(String(digit));
  await page.getByRole('button', { name: 'Verify code' }).click();
  await page.getByLabel('New password', { exact: true }).fill('NewPassword123!');
  await page.getByLabel('Confirm password', { exact: true }).fill('NewPassword123!');
  await page.getByRole('button', { name: 'Update password' }).click();
  await expect(page).toHaveURL(/\/login\?resetSuccess=true$/);
});

test('profile renders real-shaped data and changes password from Security', async ({ page }) => {
  await page.addInitScript(value => {
    localStorage.setItem('algolearner_token', 'mock-student');
    localStorage.setItem('algolearner_user', JSON.stringify(value));
  }, user);
  await page.route('**/api/v1/users/me/profile', route => route.fulfill({ json: { data: {
    ...user, fullName: 'Test Learner', shortBio: null, avatarUrl: null, joinedAt: '2026-11-11T00:00:00Z',
    isPublic: false, publicPracticeStatistics: { solved: 0 }, streak: { currentStreak: 0, highestStreak: 0 },
  } } }));
  await page.route('**/api/v1/auth/change-password', route => {
    expect(route.request().postDataJSON().currentPassword).toBe('ExamplePass123!');
    return route.fulfill({ json: { data: { success: true, message: 'Password updated. Other sessions have been signed out.' } } });
  });
  await page.goto(`${base}/profile`);
  await expect(page.getByRole('textbox', { name: 'Display name' })).toHaveValue('Test Learner');
  await page.getByRole('tab', { name: 'Security' }).click();
  await page.getByLabel('Current password', { exact: true }).fill('ExamplePass123!');
  await page.getByLabel('New password', { exact: true }).fill('ChangedPass123!');
  await page.getByLabel('Confirm new password', { exact: true }).fill('ChangedPass123!');
  await page.getByRole('button', { name: 'Change password', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Password updated.' })).toBeVisible();
});
