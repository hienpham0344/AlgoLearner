import { expect, test } from '@playwright/test';

test.skip(process.env.ALGOLEARN_LIVE_BACKEND !== '1', 'Requires the local Spring Boot/H2 integration server');

test('4.2 and 4.3 work through the running Spring Boot API', async ({ page }) => {
  const base = 'http://127.0.0.1:3101';
  const classroomName = `Live integration ${Date.now()}`;
  const signIn = async (username: string) => {
    await page.goto(`${base}/login`);
    await page.getByLabel('Username or email').fill(username);
    await page.getByLabel('Password', { exact: true }).fill('DemoPass123!');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await page.getByRole('button', { name: 'Continue to your workspace' }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
  };

  await signIn('teacher');
  await expect(page.getByRole('heading', { name: 'Keep the class moving.' })).toBeVisible();
  await page.getByRole('link', { name: 'My profile', exact: true }).first().click();
  const nameInput = page.getByRole('textbox', { name: 'Display name' });
  const originalName = await nameInput.inputValue();
  await nameInput.fill('Live integration teacher');
  const updatedProfile = page.waitForResponse(response => response.url().endsWith('/api/v1/users/me/profile')
    && response.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'Save changes' }).click();
  expect((await updatedProfile).status()).toBe(200);
  await expect(page.getByRole('status')).toContainText('Profile changes saved.');
  await page.getByRole('link', { name: 'Workspace' }).click();
  await page.getByRole('link', { name: 'My profile', exact: true }).first().click();
  await expect(nameInput).toHaveValue('Live integration teacher');
  await nameInput.fill(originalName);
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('status')).toContainText('Profile changes saved.');
  await page.getByRole('link', { name: 'Workspace' }).click();
  await page.goto(`${base}/teacher/classrooms`);
  await expect(page.getByRole('heading', { name: 'Create classroom' })).toBeVisible();
  await page.getByRole('textbox', { name: 'Classroom name' }).fill(classroomName);
  const createdResponse = page.waitForResponse(response => response.url().endsWith('/api/v1/classrooms')
    && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Create', exact: true }).click();
  expect((await createdResponse).status()).toBe(201);
  await expect(page).toHaveURL(/\/teacher\/classrooms\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { name: classroomName })).toBeVisible();
  const codeText = await page.getByText(/^Class code:/).textContent();
  const classCode = codeText?.match(/Class code:\s*([A-Z0-9]+)/)?.[1];
  expect(classCode).toMatch(/^[A-Z0-9]{8}$/);

  await page.getByRole('link', { name: 'Dashboard' }).click();
  await page.getByLabel('Account menu').click();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await signIn('student');
  await page.goto(`${base}/profile`);
  await expect(page.getByRole('textbox', { name: 'Display name' })).toHaveValue('Demo student');
  await page.goto(`${base}/student/classrooms`);
  await expect(page.getByRole('heading', { name: 'Join with class code' })).toBeVisible();
  await page.getByRole('textbox', { name: 'Class code' }).fill(classCode!);
  const joinedResponse = page.waitForResponse(response => response.url().endsWith('/api/v1/classrooms/join')
    && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Join', exact: true }).click();
  expect((await joinedResponse).status()).toBe(200);
  await expect(page).toHaveURL(/\/student\/classrooms\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { name: classroomName })).toBeVisible();
  await expect(page.getByText(/^Class code:/)).toHaveCount(0);
});
