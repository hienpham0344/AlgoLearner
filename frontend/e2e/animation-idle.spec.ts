import { expect, test } from '@playwright/test';

const base = process.env.ALGOLEARN_E2E_BASE || 'http://127.0.0.1:3100';

test('auth canvas stops repainting while idle and resumes on pointer movement', async ({ page }) => {
  await page.addInitScript(() => {
    (window as Window & { __canvasClears?: number }).__canvasClears = 0;
    const clear = CanvasRenderingContext2D.prototype.clearRect;
    CanvasRenderingContext2D.prototype.clearRect = function (...args) {
      (window as Window & { __canvasClears?: number }).__canvasClears! += 1;
      return clear.apply(this, args);
    };
  });
  await page.goto(`${base}/register`, { waitUntil: 'domcontentloaded' });
  await expect(page.locator('.auth-dot-grid canvas')).toBeVisible();
  await page.waitForTimeout(400);
  const beforeIdle = await page.evaluate(() => (window as Window & { __canvasClears?: number }).__canvasClears ?? 0);
  await page.waitForTimeout(600);
  const idleClears = await page.evaluate(before => ((window as Window & { __canvasClears?: number }).__canvasClears ?? 0) - before, beforeIdle);
  expect(idleClears).toBeLessThanOrEqual(2);

  const rect = await page.locator('.auth-dot-grid').boundingBox();
  expect(rect).not.toBeNull();
  await page.mouse.move(rect!.x + rect!.width / 2, rect!.y + rect!.height / 2);
  await page.mouse.move(rect!.x + rect!.width / 2 + 30, rect!.y + rect!.height / 2 + 20, { steps: 5 });
  const afterMove = await page.evaluate(() => (window as Window & { __canvasClears?: number }).__canvasClears ?? 0);
  expect(afterMove).toBeGreaterThan(beforeIdle + idleClears);
});
