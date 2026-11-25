import { defineConfig } from '@playwright/test';
import { existsSync } from 'node:fs';

const browserPath = process.env.PLAYWRIGHT_CHROME_PATH || (process.env.CI ? undefined : [
  '/usr/bin/google-chrome',
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
].find(path => existsSync(path)));

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  retries: 0,
  reporter: 'list',
  use: {
    browserName: 'chromium',
    launchOptions: {
      executablePath: browserPath,
      args: ['--no-sandbox'],
    },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: [
    {
      command: 'npm run dev:auth-demo -- --host 127.0.0.1 --port 3100 --strictPort',
      env: { ALGOLEARN_API_TARGET: process.env.ALGOLEARN_API_TARGET || 'http://127.0.0.1:19999' },
      url: 'http://127.0.0.1:3100/login',
      reuseExistingServer: false,
      timeout: 30_000,
    },
    {
      command: 'npm run dev:backend-mock -- --host 127.0.0.1 --port 3101 --strictPort',
      env: { ALGOLEARN_API_TARGET: process.env.ALGOLEARN_API_TARGET || 'http://127.0.0.1:19999' },
      url: 'http://127.0.0.1:3101/login',
      reuseExistingServer: false,
      timeout: 30_000,
    },
  ],
});
