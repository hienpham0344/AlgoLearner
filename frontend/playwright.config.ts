import { defineConfig } from '@playwright/test';

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
      executablePath: process.env.PLAYWRIGHT_CHROME_PATH || (process.env.CI ? undefined : '/usr/bin/google-chrome'),
      args: ['--no-sandbox'],
    },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: [
    {
      command: 'npm run dev:auth-demo -- --host 127.0.0.1 --port 3100 --strictPort',
      url: 'http://127.0.0.1:3100/login',
      reuseExistingServer: false,
      timeout: 30_000,
    },
    {
      command: 'npm run dev:backend-mock -- --host 127.0.0.1 --port 3101 --strictPort',
      url: 'http://127.0.0.1:3101/login',
      reuseExistingServer: false,
      timeout: 30_000,
    },
  ],
});
