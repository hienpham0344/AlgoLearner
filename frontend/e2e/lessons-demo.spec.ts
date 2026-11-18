import { expect, test, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.skip(!process.env.ALGOLEARN_LESSON_DEMO_URL, 'Opt in with the URL of a running npm run dev:lessons-demo server.');

test('manual lesson demo supports real browser login, teacher authoring and student completion without route mocks', async ({ page, browser }, testInfo) => {
  const base = process.env.ALGOLEARN_LESSON_DEMO_URL || 'http://127.0.0.1:3002';
  const classPath = '/classrooms/demo-class-1/lessons';
  async function signIn(target: Page, role: 'teacher' | 'student') {
    await target.goto(`${base}/login`);
    await target.getByLabel('Username or email').fill(role);
    await target.getByLabel('Password', { exact: true }).fill('DemoPass123!');
    await target.getByRole('button', { name: 'Sign in', exact: true }).click();
    await target.getByRole('button', { name: 'Continue to your workspace' }).click();
    await expect(target).toHaveURL(/\/dashboard$/);
    const menu = target.getByRole('button', { name: 'Open navigation', exact: true });
    if (await menu.isVisible()) await menu.click();
    await target.getByRole('navigation', { name: new RegExp(`${role} workspace`, 'i') }).getByRole('link', { name: 'Class lessons', exact: true }).click();
    const lessons = target.getByRole('region', { name: 'Class lessons', exact: true });
    await expect(lessons).toBeInViewport();
    expect((await new AxeBuilder({ page: target }).include('#lessons').withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()).violations).toEqual([]);
    await target.screenshot({ path: testInfo.outputPath(`dashboard-class-lessons-${role}.png`) });
    await lessons.getByRole('link', { name: /Algorithms & Data Structures/ }).click();
    await expect(target).toHaveURL(new RegExp(`/${role}${classPath}$`));
  }
  await signIn(page, 'teacher');
  await page.getByRole('link', { name: 'Create lesson', exact: true }).click();
  const title = `Manual demo verification ${Date.now()}`;
  await page.getByLabel('Lesson title').fill(title);
  await page.getByLabel('Theory & notes').fill('## A lesson to try\nThis is sample data.');
  await page.getByRole('button', { name: 'Save draft' }).click();
  await expect(page.getByRole('heading', { name: title })).toBeVisible();
  await page.getByRole('button', { name: 'Publish lesson', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(page.locator('.lesson-status')).toHaveText('Published');
  const context = await browser.newContext();
  try {
    const student = await context.newPage();
    await student.setViewportSize({ width: 390, height: 844 });
    await signIn(student, 'student');
    await student.getByRole('link', { name: new RegExp(title) }).click();
    await expect(student.getByRole('heading', { name: title })).toBeVisible();
    await student.getByRole('button', { name: 'Mark as completed', exact: true }).click();
    await expect(student.locator('.lesson-completion')).toContainText('Completed');
    await student.reload();
    await expect(student.locator('.lesson-completion')).toContainText('Completed');
    await page.getByRole('button', { name: 'Refresh progress' }).click();
    await expect(page.getByRole('region', { name: 'Student progress' })).toContainText('Demo Student');
    await expect(page.getByRole('region', { name: 'Student progress' })).toContainText('Completed');
    await page.reload();
    await expect(page.getByRole('button', { name: 'Delete lesson', exact: true })).toBeDisabled();
  } finally { await context.close(); }
});
