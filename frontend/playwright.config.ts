import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';

const externalDeployment = process.env.E2E_EXTERNAL_DEPLOYMENT === '1';
const evidenceRoot = process.env.E2E_EVIDENCE_DIR?.trim()
  ? assertOutsideRepository(process.env.E2E_EVIDENCE_DIR)
  : null;
const reportRoot = evidenceRoot ? path.join(evidenceRoot, 'report') : 'e2e-report';
const artifactRoot = evidenceRoot ? path.join(evidenceRoot, 'artifacts') : 'test-results';
const chromiumExecutable = process.env.MEDKERNEL_PLAYWRIGHT_CHROMIUM_EXECUTABLE?.trim();
const chromiumLaunchOptions = chromiumExecutable
  ? {
      executablePath: chromiumExecutable,
      args: process.env.MEDKERNEL_PLAYWRIGHT_NO_SANDBOX === '1' ? ['--no-sandbox'] : [],
    }
  : undefined;

const domesticChromiumSimulation = {
  ...devices['Desktop Chrome'],
  userAgent:
    'Mozilla/5.0 (X11; Linux aarch64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 UOSBrowser/6.0',
};

const embedHostServer = {
  command: 'node e2e/support/embed-business-host-server.mjs',
  url: 'http://127.0.0.1:4174',
  reuseExistingServer: !process.env.CI,
  timeout: 30000,
};

const localFrontendServer = {
  command: 'npm run dev',
  url: 'http://localhost:5173',
  reuseExistingServer: !process.env.CI,
  timeout: 120000,
  env: {
    MEDKERNEL_API_PROXY_TARGET:
      process.env.MEDKERNEL_API_PROXY_TARGET ||
      process.env.VITE_API_PROXY_TARGET ||
      'http://localhost:18080',
  },
};

export default defineConfig({
  testDir: './e2e',
  outputDir: artifactRoot,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [
    ['html', { open: 'never', outputFolder: reportRoot }],
    ['json', { outputFile: path.join(reportRoot, 'results.json') }],
  ],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:5173',
    ignoreHTTPSErrors: process.env.E2E_IGNORE_HTTPS_ERRORS === '1',
    ...(chromiumLaunchOptions ? { launchOptions: chromiumLaunchOptions } : {}),
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
  webServer: externalDeployment
    ? [embedHostServer]
    : [localFrontendServer, embedHostServer],
});

function assertOutsideRepository(candidate: string) {
  const resolved = path.resolve(candidate);
  const repository = path.resolve(process.cwd(), '..');
  const relative = path.relative(repository, resolved);
  if (relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative))) {
    throw new Error('E2E_EVIDENCE_DIR 必须位于代码仓库之外');
  }
  return resolved;
}
