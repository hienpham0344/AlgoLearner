import { expect, test } from '@playwright/test';
import { readFile } from 'node:fs/promises';

const username = 'hienpham0344@gmail.com';

test('profile preview keeps long usernames on one line at narrow widths and zoom', async ({ page }) => {
  const css = await readFile(new URL('../src/features/profile/styles/profile.css', import.meta.url), 'utf8');
  await page.setContent(`
    <style>${css}</style>
    <main class="account-page" style="padding: 16px">
      <aside class="folder-identity-preview" style="width: min(100%, 340px)">
        <div class="identity-specimen-card">
          <div class="identity-meta">
            <h3 class="identity-name" data-username="true">${username}</h3>
            <span class="identity-handle">@${username}</span>
          </div>
        </div>
      </aside>
    </main>
  `);
  const name = page.locator('.identity-name');
  const handle = page.locator('.identity-handle');
  await expect(name).toHaveText(username);
  await expect(handle).toHaveText(`@${username}`);

  for (const width of [320, 375, 768, 1024, 1440]) {
    await test.step(`${width}px`, async () => {
      await page.setViewportSize({ width, height: 900 });
      for (const element of [name, handle]) {
        const dimensions = await element.evaluate(node => ({
          height: node.clientHeight,
          lineHeight: Number.parseFloat(getComputedStyle(node).lineHeight),
          width: node.getBoundingClientRect().width,
          parentWidth: node.parentElement!.getBoundingClientRect().width,
        }));
        expect(dimensions.height).toBeLessThanOrEqual(dimensions.lineHeight + 1);
        expect(dimensions.width).toBeLessThanOrEqual(dimensions.parentWidth + 1);
      }
    });
  }

  await page.setViewportSize({ width: 1024, height: 900 });
  await page.evaluate(() => { document.documentElement.style.zoom = '200%'; });
  for (const element of [name, handle]) {
    const dimensions = await element.evaluate(node => ({
      height: node.clientHeight,
      lineHeight: Number.parseFloat(getComputedStyle(node).lineHeight),
    }));
    expect(dimensions.height).toBeLessThanOrEqual(dimensions.lineHeight + 1);
  }
});
