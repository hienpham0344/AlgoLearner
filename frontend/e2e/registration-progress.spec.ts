import { expect, test, type Route } from '@playwright/test';

const base = process.env.ALGOLEARN_E2E_BASE || 'http://127.0.0.1:3100';

test('registration explains that email delivery is still in progress', async ({ page }) => {
  let pendingRegistration: Route | undefined;
  await page.route('**/api/v1/auth/register', route => { pendingRegistration = route; });
  await page.goto(`${base}/register`, { waitUntil: 'domcontentloaded' });

  await page.getByLabel('Email address').fill('progress@example.test');
  await page.getByLabel('Username').fill('progress-user');
  await page.getByLabel('Password', { exact: true }).fill('ExamplePass123!');
  await page.getByLabel('Confirm password').fill('ExamplePass123!');
  await page.getByLabel(/I agree to the Terms of Service/).check();
  await page.getByLabel(/I accept the Privacy Policy/).check();
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByRole('status').filter({ hasText: 'sending a verification code' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Creating account…' })).toBeDisabled();
  expect(pendingRegistration).toBeDefined();

  await pendingRegistration!.fulfill({ json: { data: { email: 'progress@example.test', message: 'Code sent' } } });
  await expect(page).toHaveURL(/\/verify-otp\?email=progress%40example\.test&purpose=REGISTER$/);
});

test('registration timeout does not silently retry an uncertain write', async ({ page }) => {
  let requests = 0;
  await page.route('**/api/v1/auth/register', route => {
    requests += 1;
    return route.fulfill({ status: 502, json: { success: false, errorCode: 'BACKEND_UNREACHABLE', message: 'Cannot complete the backend request.' } });
  });
  await page.goto(`${base}/register`, { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Email address').fill('timeout@example.test');
  await page.getByLabel('Username').fill('timeout-user');
  await page.getByLabel('Password', { exact: true }).fill('ExamplePass123!');
  await page.getByLabel('Confirm password').fill('ExamplePass123!');
  await page.getByLabel(/I agree to the Terms of Service/).check();
  await page.getByLabel(/I accept the Privacy Policy/).check();
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByText(/Check your inbox before trying again/)).toBeVisible();
  expect(requests).toBe(1);
});
