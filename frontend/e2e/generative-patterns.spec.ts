import { expect, test } from '@playwright/test';

const base = 'http://127.0.0.1:3000';

test('AGPS Pattern Lab and Classroom Atelier remain stable and responsive', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`${base}/dev/pattern-lab`);
  await expect(page.getByRole('heading', { name: 'Pattern Lab' })).toBeVisible();
  await expect(page.locator('.pattern-lab-variant')).toHaveCount(5);
  await page.getByLabel('Family').selectOption('relational-matrix');
  await page.getByLabel('Surface').selectOption('light');
  await page.getByLabel('Seed').fill('2048');
  await expect(page.locator('.pattern-lab-stage svg')).toHaveAttribute('data-family', 'relational-matrix');
  await page.screenshot({ path: '../docs/feature/agps-pattern-lab.png', animations: 'disabled' });
  const start = Date.now();
  for (const count of [2, 10, 30, 100]) {
    await page.getByLabel('Dataset size').selectOption(String(count));
    await expect(page.locator('.pattern-lab-swatch')).toHaveCount(count);
  }
  console.log(`AGPS dataset 2/10/30/100 render: ${Date.now() - start} ms`);
  expect(await page.locator('.pattern-lab-swatch svg[aria-hidden="true"]').count()).toBe(100);
  const ids = await page.locator('.pattern-lab-swatch pattern').evaluateAll(elements => elements.map(element => element.id));
  expect(new Set(ids).size).toBe(ids.length);

  await page.goto(`${base}/login`);
  await page.getByLabel('Username or email').fill('learner@example.test');
  await page.getByLabel('Password', { exact: true }).fill('DemoPass123!');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.goto(`${base}/practice`);
  await expect(page.getByRole('heading', { name: 'Your classrooms.' })).toBeVisible();
  const covers = page.locator('.atelier-cover');
  await expect(covers).toHaveCount(2);
  await expect(covers.nth(0)).toHaveAttribute('data-family', 'tree');
  await expect(covers.nth(1)).toHaveAttribute('data-family', 'architecture');
  await expect(covers.nth(0)).toHaveAttribute('data-tone', 'dark');
  await expect(covers.nth(1)).toHaveAttribute('data-tone', 'light');
  const identity = await covers.evaluateAll(elements => elements.map(element => ({ id: element.querySelector('a')?.getAttribute('href'), family: element.getAttribute('data-family'), tone: element.getAttribute('data-tone'), geometry: element.querySelector('.agps-geometry')?.innerHTML })));
  await page.screenshot({ path: '../docs/feature/agps-atelier-after.png', animations: 'disabled' });
  await page.reload();
  await expect(covers).toHaveCount(2);
  const refreshed = await covers.evaluateAll(elements => elements.map(element => ({ id: element.querySelector('a')?.getAttribute('href'), family: element.getAttribute('data-family'), tone: element.getAttribute('data-tone'), geometry: element.querySelector('.agps-geometry')?.innerHTML })));
  expect(refreshed).toEqual(identity);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  expect(await covers.first().locator('.agps-emphasis').first().evaluate(element => getComputedStyle(element).transitionDuration)).toBe('0s');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: '../docs/feature/agps-atelier-mobile-after.png', animations: 'disabled' });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await covers.first().getByRole('link', { name: 'Open library' }).click();
  await expect(page).toHaveURL(/\/student\/classrooms\/.*\/practice/);
});
