import { defineConfig, devices } from '@playwright/test';

const domesticChromiumSimulation = {
  ...devices['Desktop Chrome'],
  userAgent:
    'Mozilla/5.0 (X11; Linux aarch64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 UOSBrowser/6.0',
};

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [
    ['html', { open: 'never', outputFolder: 'e2e-report' }],
    ['json', { outputFile: 'e2e-report/results.json' }],
  ],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: '国产 Chromium 内核仿真（非现场认证）',
      use: domesticChromiumSimulation,
    },
  ],
  webServer: [
    {
      command: 'npm run dev',
      url: 'http://localhost:5173',
      reuseExistingServer: !process.env.CI,
      timeout: 120000,
      env: {
        MEDKERNEL_API_PROXY_TARGET:
          process.env.MEDKERNEL_API_PROXY_TARGET ||
          process.env.VITE_API_PROXY_TARGET ||
          'http://127.0.0.1:18081',
      },
    },
    {
      command: 'node e2e/support/embed-business-host-server.mjs',
      url: 'http://127.0.0.1:4174',
      reuseExistingServer: !process.env.CI,
      timeout: 30000,
    },
  ],
});
