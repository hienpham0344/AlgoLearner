import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import type { ApiResponse } from '../src/types/api';
import type { AuthResponseData } from '../src/features/auth/types';
import type { ClassroomRecord } from '../src/features/classroom/api/classroomApi';
import type { Lesson } from '../src/features/classroom/lessons/types';

// Explicit opt-in: requires the confirmed #12 contract and real local BE 4.1/4.3/4.5.
// Never substitute route mocks or demo credentials for this integration gate.
test.skip(process.env.ALGOLEARN_LIVE_LESSONS !== '1', 'Blocked: issue #12 and the lesson contract are not available yet.');

test('LIVE LES: teacher authoring and lifecycle preserve student completion through the real API', async ({ browser, page }) => {
  const base = process.env.ALGOLEARN_LESSON_BASE_URL || 'http://127.0.0.1:3101';
  function credential(name: string) {
    const value = process.env[name]; if (!value) throw new Error(`Set ${name} for the real lesson integration test.`); return value;
  }
  async function signIn(context: BrowserContext, username: string, password: string) {
    const response = await context.request.post(`${base}/api/v1/auth/login`, { data: { usernameOrEmail: username, password, rememberMe: false } });
    expect(response.status(), 'Real backend login must succeed without mocks').toBe(200);
    const { data } = await response.json() as ApiResponse<AuthResponseData>;
    await context.addInitScript(({ data }) => {
      localStorage.setItem('algolearner_token', data.token);
      localStorage.setItem('algolearner_user', JSON.stringify(data.user));
    }, { data });
    return data;
  }
  const teacher = await signIn(page.context(), credential('ALGOLEARN_LESSON_TEACHER'), credential('ALGOLEARN_LESSON_TEACHER_PASSWORD'));
  const studentContext = await browser.newContext();
  let classroom: ClassroomRecord | undefined;
  try {
    const student = await signIn(studentContext, credential('ALGOLEARN_LESSON_STUDENT'), credential('ALGOLEARN_LESSON_STUDENT_PASSWORD'));
    const createdClass = await page.request.post(`${base}/api/v1/classrooms`, { headers: { Authorization: `Bearer ${teacher.token}` }, data: { name: `LES integration ${Date.now()}`, description: 'Isolated lesson integration test', joinMode: 'AUTO_JOIN', codeExpiresAt: null } });
    expect(createdClass.status()).toBe(201); classroom = (await createdClass.json() as ApiResponse<ClassroomRecord>).data;
    const joined = await studentContext.request.post(`${base}/api/v1/classrooms/join`, { headers: { Authorization: `Bearer ${student.token}` }, data: { classCode: classroom.classCode } }); expect(joined.ok()).toBe(true);
    const path = `${base}/teacher/classrooms/${classroom.id}/lessons`;
    await page.goto(`${path}/new`); await page.getByLabel('Lesson title').fill('Live lesson: recursion');
    await page.getByLabel('Theory & notes').fill('## A small example\nRead this lesson before marking it completed.');
    const savedResponse = page.waitForResponse(response => response.url().endsWith(`/classrooms/${classroom!.id}/lessons`) && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Save draft' }).click();
    const saved = await savedResponse; expect(saved.status()).toBe(201);
    const lesson = (await saved.json() as ApiResponse<Lesson>).data; expect(lesson.status).toBe('DRAFT');
    const studentPage = await studentContext.newPage();
    const studentPath = `${base}/student/classrooms/${classroom.id}/lessons/${lesson.id}`;
    await studentPage.goto(studentPath); await expect(studentPage.getByRole('alert')).toBeVisible();
    const transition = async (target: Page, label: string) => {
      await target.getByRole('button', { name: label, exact: true }).click();
      await target.getByRole('button', { name: 'Confirm', exact: true }).click();
      await expect(target.getByRole('dialog')).not.toBeVisible();
    };
    await transition(page, 'Publish lesson');
    await studentPage.reload(); await expect(studentPage.getByRole('heading', { name: 'Live lesson: recursion' })).toBeVisible();
    await studentPage.getByRole('button', { name: 'Mark as completed', exact: true }).click();
    await expect(studentPage.locator('.lesson-completion')).toContainText('Completed');
    await studentPage.reload(); await expect(studentPage.locator('.lesson-completion')).toContainText('Completed');
    await transition(page, 'Move to draft'); await studentPage.reload(); await expect(studentPage.getByRole('alert')).toBeVisible();
    await transition(page, 'Archive lesson'); await transition(page, 'Restore lesson'); await expect(page.locator('.lesson-status')).toHaveText('Draft');
    await transition(page, 'Publish lesson'); await transition(page, 'Archive lesson');
    await studentPage.reload(); await expect(studentPage.getByRole('alert')).toBeVisible();
    await transition(page, 'Restore lesson'); await expect(page.locator('.lesson-status')).toHaveText('Published');
    await studentPage.reload(); await expect(studentPage.locator('.lesson-completion')).toContainText('Completed');
    await page.getByRole('button', { name: 'Refresh progress' }).click();
    await expect(page.getByRole('region', { name: 'Student progress' })).toContainText('Completed');
    await expect(page.getByRole('button', { name: 'Delete lesson', exact: true })).toBeDisabled();
  } finally {
    if (classroom) {
      const cleanup = await page.request.post(`${base}/api/v1/classrooms/${classroom.id}/archive`, { headers: { Authorization: `Bearer ${teacher.token}` } });
      expect(cleanup.ok(), 'Archive the isolated test classroom after integration testing').toBe(true);
    }
    await studentContext.close();
  }
});
