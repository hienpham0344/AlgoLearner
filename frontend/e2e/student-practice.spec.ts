import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

const base = 'http://127.0.0.1:3100';
const nowIso = new Date().toISOString();

const pagination = (total: number) => ({
  page: 0,
  size: 20,
  totalElements: total,
  totalPages: total ? 1 : 0,
  isFirst: true,
  isLast: true,
});

const envelope = <T>(data: T) => ({
  success: true,
  data,
  timestamp: nowIso,
});

const problemOne = {
  id: 'p1',
  classroomId: 'c1',
  lessonId: null,
  title: 'Longest Subarray Within Budget',
  description:
    'Given an array of $n$ positive integers and budget $B$, print the maximum valid window length.\n\n### Constraints\n- $1 \\le n \\le 2 \\cdot 10^5$\n\n$$x^2 + y^2 = z^2$$\n\n```text\n$n$ stays literal in code\n```\n\nEscaped \\$ remains text.\n\n<script>alert(1)</script>',
  problemType: 'STANDARD_IO',
  status: 'PUBLISHED',
  available: true,
  allowedLanguages: ['JAVA', 'PYTHON'],
  timeLimitMs: 1500,
  memoryLimitMb: 256,
  completionStatus: 'NOT_COMPLETED',
  createdAt: nowIso,
  updatedAt: nowIso,
  starterCode: {
    JAVA: 'public class Main {\n    public static void main(String[] args) {\n        System.out.println(3);\n    }\n}',
    PYTHON: 'def solve():\n    print(3)\n\nif __name__ == "__main__":\n    solve()\n',
  },
  testcaseVersion: 2,
  sampleTestcases: [
    { id: 's1', input: '5 8\n3 1 2 7 4', expectedOutput: '3', orderIndex: 0 },
    { id: 's2', input: '4 3\n5 6 7 8', expectedOutput: '0', orderIndex: 1 },
  ],
};

const problemTwo = {
  id: 'p2',
  classroomId: 'c1',
  lessonId: null,
  title: 'Balanced Delimiter Depth',
  description: 'Return maximum nesting depth for balanced brackets, or `-1` if invalid.',
  problemType: 'FUNCTION_METHOD',
  status: 'PUBLISHED',
  available: true,
  allowedLanguages: ['JAVA', 'PYTHON'],
  timeLimitMs: 1000,
  memoryLimitMb: 128,
  completionStatus: 'COMPLETED',
  createdAt: nowIso,
  updatedAt: nowIso,
  starterCode: {
    JAVA: 'public class Main {}',
    PYTHON: 'print(0)',
  },
  testcaseVersion: 1,
  sampleTestcases: [{ id: 's3', input: '{[()]}', expectedOutput: '3', orderIndex: 0 }],
};

const acceptedSubmission = {
  id: 'sub-101',
  practiceId: 'p1',
  studentId: 'student1',
  studentUsername: 'learner',
  language: 'JAVA',
  code: 'public class Main {\n    public static void main(String[] args) {\n        System.out.println(3);\n    }\n}',
  testcaseVersion: 2,
  verdict: 'ACCEPTED',
  testsPassed: 10,
  totalTests: 10,
  runtimeMs: 24,
  memoryKb: 15360,
  rejudgeStatus: null,
  submittedAt: nowIso,
};

test('student practice studio, inspector, coding workspace, error states, ledger, accessibility, and 6-viewport responsiveness', async ({
  page,
}) => {
  test.setTimeout(150_000);
  await page.setViewportSize({ width: 1440, height: 900 });

  await page.goto(`${base}/login`);
  await page.getByLabel('Username or email').fill('learner@example.test');
  await page.getByLabel('Password', { exact: true }).fill('DemoPass123!');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).toHaveURL(/\/dashboard/);

  let runs = 0;
  let submits = 0;
  let failNextSubmitWith503 = true;

  await page.route('**/api/v1/classrooms**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const method = route.request().method();

    if (path === '/api/v1/classrooms') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          envelope({
            items: [
              {
                id: 'c1',
                name: 'Algorithms 101',
                description: 'Data Structures & Sliding Window Studio',
                status: 'ACTIVE',
                ownerId: 'teacher1',
              },
            ],
            pagination: pagination(1),
          }),
        ),
      });
      return;
    }

    if (path === '/api/v1/classrooms/c1') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          envelope({
            id: 'c1',
            name: 'Algorithms 101',
            description: 'Data Structures & Sliding Window Studio',
            status: 'ACTIVE',
            ownerId: 'teacher1',
          }),
        ),
      });
      return;
    }

    if (path === '/api/v1/classrooms/c1/practices') {
      const currentP1 = submits > 0 ? { ...problemOne, completionStatus: 'COMPLETED' } : problemOne;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(envelope({ items: [currentP1, problemTwo], pagination: pagination(2) })),
      });
      return;
    }

    if (path === '/api/v1/classrooms/c1/practices/p1') {
      const currentP1 = submits > 0 ? { ...problemOne, completionStatus: 'COMPLETED' } : problemOne;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(envelope(currentP1)),
      });
      return;
    }

    if (path === '/api/v1/classrooms/c1/practices/p2') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(envelope(problemTwo)),
      });
      return;
    }

    if (path.endsWith('/run') && method === 'POST') {
      runs += 1;
      if (runs === 1) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(
            envelope({
              verdict: 'COMPILATION_ERROR',
              cases: [
                {
                  index: 0,
                  input: '5 8\n3 1 2 7 4',
                  expectedOutput: '3',
                  verdict: 'COMPILATION_ERROR',
                  stdout: '',
                  stderr: "Main.java:3: error: ';' expected\n        System.out.println(3)\n                             ^",
                  runtimeMs: null,
                },
              ],
            }),
          ),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          envelope({
            verdict: 'ACCEPTED',
            cases: [
              {
                index: 0,
                input: '5 8\n3 1 2 7 4',
                expectedOutput: '3',
                verdict: 'ACCEPTED',
                stdout: '3',
                stderr: '',
                runtimeMs: 14,
              },
              {
                index: 1,
                input: '4 3\n5 6 7 8',
                expectedOutput: '0',
                verdict: 'ACCEPTED',
                stdout: '0',
                stderr: '',
                runtimeMs: 18,
              },
            ],
          }),
        ),
      });
      return;
    }

    if (path.endsWith('/submit') && method === 'POST') {
      if (failNextSubmitWith503) {
        failNextSubmitWith503 = false;
        await route.fulfill({
          status: 503,
          contentType: 'application/json',
          body: JSON.stringify({
            success: false,
            errorCode: 'JUDGE_SERVICE_UNAVAILABLE',
            message:
              'The isolated execution worker is busy. Your code was not marked incorrect and no failed submission was recorded.',
            timestamp: nowIso,
          }),
        });
        return;
      }
      submits += 1;
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(envelope(acceptedSubmission)),
      });
      return;
    }

    if (path.endsWith('/submissions/me')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          envelope({
            items: submits > 0 ? [acceptedSubmission] : [],
            pagination: pagination(submits > 0 ? 1 : 0),
          }),
        ),
      });
      return;
    }

    if (path.endsWith('/submissions/sub-101')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(envelope(acceptedSubmission)),
      });
      return;
    }

    await route.continue();
  });

  // 1. Practice Studio & Class Chooser (STU-P01 + STU-P08)
  await page.goto(`${base}/practice`);
  await expect(page.getByRole('heading', { name: 'Your classrooms.' })).toBeVisible();
  await expect(page.getByText(/\d+ enrolled/)).toBeVisible();
  await expect(page.locator('.atelier-cover').first()).toBeVisible();
  expect((await page.locator('.atelier-cover').first().boundingBox())?.y).toBeLessThan(900);
  await page.screenshot({ path: '../docs/feature/classroom-atelier-after.png' });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator('.atelier-cover').first()).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: '../docs/feature/classroom-atelier-mobile-after.png' });
  await page.setViewportSize({ width: 1440, height: 900 });
  // Offline demo mode lists prototype classrooms; open the API-backed class library directly
  await page.goto(`${base}/student/classrooms/c1/practice`);
  await expect(page.getByRole('region', { name: 'Class problem library' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'All classrooms' })).toBeVisible();
  await page.screenshot({ path: '../docs/feature/open-textbook-after.png', animations: 'disabled' });
  const workbenchBounds = await page.locator('.open-textbook').boundingBox();
  expect(workbenchBounds).not.toBeNull();
  expect(Math.abs((workbenchBounds?.y ?? 0) + (workbenchBounds?.height ?? 0) - 900)).toBeLessThan(24);
  expect(
    await page.evaluate(() => document.documentElement.scrollHeight <= window.innerHeight),
    'Expected no outer vertical scrollbar on Open Textbook desktop (1440x900)',
  ).toBeTruthy();

  await page.setViewportSize({ width: 1280, height: 720 });
  expect(
    await page.evaluate(() => document.documentElement.scrollHeight <= window.innerHeight),
    'Expected no outer vertical scrollbar on Open Textbook desktop (1280x720)',
  ).toBeTruthy();
  await page.setViewportSize({ width: 1440, height: 900 });
  const formatFilter = page.getByRole('button', { name: /Problem format:/ });
  await formatFilter.click();
  const formatList = page.getByRole('listbox', { name: 'Problem format' });
  await expect(formatList).toBeVisible();
  const popupBounds = await formatList.boundingBox();
  expect(popupBounds).not.toBeNull();
  expect((popupBounds?.x ?? -1) + (popupBounds?.width ?? 0)).toBeLessThanOrEqual(1440);
  await page.keyboard.press('f');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('option', { name: /Balanced Delimiter Depth/ })).toBeVisible();
  await expect(page.getByRole('option', { name: /Longest Subarray Within Budget/ })).toHaveCount(0);
  await formatFilter.click();
  await page.keyboard.press('Home');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('option', { name: /Longest Subarray Within Budget/ })).toBeVisible();
  await formatFilter.click();
  await page.keyboard.press('Escape');
  await expect(formatList).toHaveCount(0);
  await expect(page.getByRole('region', { name: /Problem preview:/ })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('link', { name: 'All classrooms' })).toBeVisible();
  await page.getByRole('link', { name: 'All classrooms' }).click();
  await expect(page).toHaveURL(`${base}/practice`);
  await page.goto(`${base}/student/classrooms/c1/practice`);
  await expect(page.getByRole('option', { name: /Longest Subarray Within Budget/ })).toBeVisible();
  await expect(page.getByRole('option', { name: /Balanced Delimiter Depth/ })).toBeVisible();
  await page.getByRole('option', { name: /Balanced Delimiter Depth/ }).click();
  await expect(page.getByRole('region', { name: 'Problem preview: Balanced Delimiter Depth' })).toContainText('Function / Method');
  await expect(page.getByRole('region', { name: 'Problem preview: Balanced Delimiter Depth' })).toContainText('{[()]}');
  await expect(page).toHaveURL(/selected=p2/);
  await page.getByRole('option', { name: /Longest Subarray Within Budget/ }).click();
  await expect(page.getByRole('region', { name: 'Problem preview: Longest Subarray Within Budget' })).toContainText('5 8');
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('button', { name: 'Back to problems' })).toBeVisible();
  await page.screenshot({ path: '../docs/feature/open-textbook-mobile-after.png', animations: 'disabled' });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.getByRole('button', { name: 'Back to problems' }).click();
  await expect(page.getByRole('option', { name: /Longest Subarray Within Budget/ })).toBeVisible();
  expect(await page.locator('.open-textbook-index-list').evaluate(element => getComputedStyle(element).overflowY)).toBe('visible');
  const mobileStatus = page.getByRole('button', { name: /Status filter:/ });
  await mobileStatus.click();
  await expect(page.getByRole('listbox', { name: 'Status filter' })).toBeVisible();
  await page.keyboard.press('Escape');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.screenshot({ path: '../docs/feature/open-textbook-mobile-index-after.png', animations: 'disabled' });
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.reload();
  await expect(page.getByRole('option', { name: /Longest Subarray Within Budget/ })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Problem preview: Longest Subarray Within Budget' })).toHaveCount(0);
  await page.getByRole('option', { name: /Longest Subarray Within Budget/ }).click();
  await expect(page.getByRole('region', { name: 'Problem preview: Longest Subarray Within Budget' })).toBeVisible();
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.getByRole('option', { name: /Longest Subarray Within Budget/ }).click();

  // 2. Search, Filters & Clear Filters
  await page.getByPlaceholder('Search problems…').fill('NonExistentTopic');
  await expect(page.getByText('No problems match your current search or filters.')).toBeVisible();
  await page.getByRole('button', { name: 'Clear filters' }).click();
  await expect(page.getByRole('option', { name: /Longest Subarray Within Budget/ })).toBeVisible();

  await page.getByRole('button', { name: 'Unsolved', exact: true }).click();
  await expect(page.getByRole('option', { name: /Longest Subarray Within Budget/ })).toBeVisible();
  await expect(page.getByRole('option', { name: /Balanced Delimiter Depth/ })).toHaveCount(0);

  // 3. Open Textbook reader and shared Problem Folio
  const inspector = page.getByRole('region', {
    name: 'Problem preview: Longest Subarray Within Budget',
  });
  await expect(inspector).toBeVisible();
  await expect(inspector.getByText('Example 1')).toBeVisible();
  await page.screenshot({ path: '../docs/feature/open-textbook-filtered-after.png', animations: 'disabled' });
  await inspector.getByRole('button', { name: 'Read full statement' }).click();
  await expect(page.getByRole('dialog', { name: /Full statement: Longest Subarray/ })).toBeVisible();
  await expect(page.getByRole('dialog').getByText('5 8\n3 1 2 7 4')).toBeVisible();
  await page.screenshot({ path: '../docs/feature/open-textbook-folio-after.png', animations: 'disabled' });
  await page.getByRole('button', { name: 'Close full statement' }).click();
  await expect(inspector).toBeVisible();

  // Accessibility check on Practice Studio + open Inspector
  const studioAxe = await new AxeBuilder({ page })
    .include('.practice-studio')
    .disableRules(['color-contrast'])
    .analyze();
  expect(
    studioAxe.violations.filter(v => v.impact === 'critical' || v.impact === 'serious'),
  ).toEqual([]);

  await page.keyboard.press('Escape');
  await expect(inspector).toHaveCount(0);
  await page.getByRole('button', { name: /Reopen Longest Subarray/ }).click();

  // Navigate into Focus Coding Workspace preserving filter and selection.
  await inspector.getByRole('link', { name: /Start solving/ }).click();

  // 4. Editorial IDE & Immersive Focus Workspace (STU-P03)
  await expect(
    page.getByRole('heading', { name: 'Longest Subarray Within Budget', level: 1 }),
  ).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('.practice-ws-topbar')).toHaveAttribute('data-tone', 'light');
  await expect(page.locator('.monaco-editor .view-line').first()).toContainText('public class Main');
  await expect(page.locator('.practice-statement-scroll .katex').first()).toBeVisible();
  await expect(page.locator('.practice-statement-scroll .katex-display')).toBeVisible();
  await expect(page.locator('.practice-statement-scroll pre code')).toContainText('$n$ stays literal in code');
  await expect(page.locator('.practice-statement-scroll script')).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Class problem library' })).toBeVisible();

  // Default compact console bar renders Example chips & Ready status without crowding editor
  const consoleRegion = page.getByRole('region', { name: 'Execution console' });
  await expect(consoleRegion).toHaveAttribute('data-console-state', 'compact');
  await expect(consoleRegion.getByRole('button', { name: 'Example 1' })).toBeVisible();

  // Accessible keyboard splitter resize (36% default -> 38%)
  const hSplitter = page.getByRole('separator', { name: 'Resize problem and code panels' });
  const initialWidth = Number(await hSplitter.getAttribute('aria-valuenow'));
  expect(initialWidth).toBe(36);
  await hSplitter.focus();
  await page.keyboard.press('ArrowRight');
  expect(Number(await hSplitter.getAttribute('aria-valuenow'))).toBe(initialWidth + 2);

  // Dark FilterSelect language switching preserves per-language starter/draft state
  await page.getByRole('button', { name: /Programming language:/ }).click();
  await page.getByRole('listbox', { name: 'Programming language' }).getByRole('option', { name: 'Python 3' }).click();
  await expect(page.locator('.monaco-editor .view-line').first()).toContainText('def solve');
  await page.getByRole('button', { name: /Programming language:/ }).click();
  await page.getByRole('listbox', { name: 'Programming language' }).getByRole('option', { name: 'Java 21' }).click();
  await expect(page.locator('.monaco-editor .view-line').first()).toContainText('public class Main');

  // Immersive Focus Mode + Apple Quick Look Floating Problem Reference
  await page.getByRole('button', { name: /Workspace layout:/ }).click();
  await page.getByRole('listbox', { name: 'Workspace layout' }).getByRole('option', { name: /Focus/ }).click();
  await expect(page.locator('.practice-ws-topbar')).toHaveAttribute('data-tone', 'dark');
  await expect(page.getByRole('region', { name: 'Code editor' })).toBeVisible();
  await expect(consoleRegion).toBeHidden();

  await page.getByRole('button', { name: 'Open problem reference' }).click();
  const quickLook = page.getByRole('region', { name: 'Problem reference: Longest Subarray Within Budget' });
  await expect(quickLook).toBeVisible();
  await expect(quickLook.getByText('Example 1')).toBeVisible();
  await page.screenshot({ path: '../docs/feature/practice-workspace-focus-after.png' });

  // Axe scan in Focus Mode with Quick Look open
  const focusAxe = await new AxeBuilder({ page })
    .include('.practice-focus-shell')
    .disableRules(['color-contrast'])
    .analyze();
  expect(
    focusAxe.violations.filter(v => v.impact === 'critical' || v.impact === 'serious'),
  ).toEqual([]);

  await quickLook.getByRole('button', { name: /Dock/ }).click();
  await expect(page.locator('.practice-ws-topbar')).toHaveAttribute('data-tone', 'light');
  await expect(page.getByRole('heading', { name: 'Longest Subarray Within Budget', level: 1 })).toBeVisible();

  // Console mode auto-expands execution console
  await page.getByRole('button', { name: /Workspace layout:/ }).click();
  await page.getByRole('listbox', { name: 'Workspace layout' }).getByRole('option', { name: /Console/ }).click();
  await expect(consoleRegion).toHaveAttribute('data-console-state', 'expanded');
  await page.getByRole('button', { name: /Workspace layout:/ }).click();
  await page.getByRole('listbox', { name: 'Workspace layout' }).getByRole('option', { name: /Split/ }).click();

  // Collapse & Expand console toggle
  await page.getByRole('button', { name: 'Collapse console' }).click();
  await expect(consoleRegion).toHaveAttribute('data-console-state', 'collapsed');
  await page.getByRole('button', { name: 'Expand console' }).click();
  await expect(consoleRegion).toHaveAttribute('data-console-state', 'expanded');

  // 5. Trial Run: Compilation Error Jump-to-Line + Accepted Trial Run (STU-P04)
  await page.getByRole('button', { name: /Run Code/i }).click();
  await expect(page.getByText('Diagnostics · Line 3')).toBeVisible();
  await page.getByRole('button', { name: 'Jump to line 3' }).click();
  await expect(page.locator('.practice-ws-statusbar')).toContainText('Ln 3,');

  await page.getByRole('button', { name: /Run Code/i }).click();
  await expect(page.getByText('Trial run · no official submission created')).toBeVisible();
  await expect(page.getByText('2/2 cases matched')).toBeVisible();
  expect(runs).toBe(2);
  expect(submits).toBe(0);

  // Capture desktop Editorial IDE workspace screenshot (1440x900)
  await page.screenshot({ path: '../docs/feature/practice-workspace-after.png' });

  // Axe scan on Editorial IDE split workspace
  const workspaceAxe = await new AxeBuilder({ page })
    .include('.practice-focus-shell')
    .disableRules(['color-contrast'])
    .analyze();
  expect(
    workspaceAxe.violations.filter(v => v.impact === 'critical' || v.impact === 'serious'),
  ).toEqual([]);

  // 6. Infrastructure 503 Error Handling (never blames student solution) + Retry -> Accepted Submit
  await page.getByRole('button', { name: 'Submit solution', exact: true }).click();
  await expect(page.getByText('Code Judge temporarily unavailable')).toBeVisible();
  await expect(page.getByText(/Your code was not marked incorrect/)).toBeVisible();
  expect(submits).toBe(0);

  await page.getByRole('button', { name: 'Retry' }).click();
  await expect(page.getByText('OFFICIAL SUBMISSION SAVED', { exact: true })).toBeVisible();
  await expect(page.getByText('Practice completed')).toBeVisible();
  expect(submits).toBe(1);

  // 7. Submission History Ledger (STU-P06) & Read-Only Detail Snapshot (STU-P07)
  await page.getByRole('button', { name: /View submission/ }).click();
  await expect(page.getByRole('heading', { name: 'Submitted code · JAVA' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Historical source code' })).toContainText(
    'System.out.println(3);',
  );
  await page.getByRole('button', { name: 'Close detail' }).click();
  await expect(page.getByRole('heading', { name: 'My submissions' })).toBeVisible();
  await expect(page.getByText('LATEST SUBMISSION')).toBeVisible();

  // 8. Responsive Layout QA across all required desktop, laptop, tablet, and mobile viewports
  const viewports: Array<[number, number]> = [
    [1920, 1080],
    [1440, 900],
    [1366, 768],
    [1280, 720],
    [1024, 768],
    [768, 1024],
    [390, 844],
  ];

  for (const [width, height] of viewports) {
    await page.setViewportSize({ width, height });
    const fitsViewport = await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    );
    expect(fitsViewport, `Expected zero horizontal overflow at ${width}x${height}`).toBeTruthy();
  }

  // Mobile (<960px) task bar preserves code state and provides sticky Run/Submit
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('tab', { name: 'Code', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Code editor' })).toBeVisible();
  await expect(page.locator('.monaco-editor .view-line').first()).toContainText('public class Main');
  await page.screenshot({ path: '../docs/feature/practice-workspace-mobile-after.png' });

  // 9. Return to library preserves active ?status=unsolved filter context
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.getByRole('link', { name: 'Class problem library' }).first().click();
  await expect(page).toHaveURL(/\/student\/classrooms\/c1\/practice\?.*status=unsolved/);
  await expect(page).toHaveURL(/selected=p1/);
  await page.goto(`${base}/student/classrooms/c1/practice/p1`);
  await page.reload();
  await expect(page.getByRole('link', { name: 'Class problem library' })).toHaveAttribute('href', '/student/classrooms/c1/practice');

  // A public problem returns to the public catalog rather than a classroom.
  await page.goto(`${base}/practice?scope=public`);
  await expect(page.getByRole('heading', { name: 'Public catalog.' })).toBeVisible();
  await expect(page.getByText(/1 solved/)).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Your classrooms' })).toHaveCount(0);
  await expect(page.getByRole('columnheader', { name: 'Format' })).toHaveCount(0);
  await page.screenshot({ path: '../docs/feature/practice-public-catalog-after.png' });
  await page.getByRole('link', { name: /Start solving/ }).first().click();
  await expect(page.getByLabel('Custom input')).toHaveCount(0);
  await page.reload();
  await page.getByRole('link', { name: 'Public catalog' }).first().click();
  await expect(page).toHaveURL(/\/practice\?scope=public/);
});
