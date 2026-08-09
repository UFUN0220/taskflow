import { defineConfig, devices } from '@playwright/test'
import { existsSync } from 'node:fs'

const localBrowsers = 'F:/newinstall/playwright-browsers'
if (process.platform === 'win32' && existsSync(localBrowsers)) {
  process.env.PLAYWRIGHT_BROWSERS_PATH = localBrowsers
}

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  outputDir: 'test-results',
  use: {
  baseURL: process.env.TASKFLOW_ACCEPTANCE_BASE_URL ?? process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5173',
    ...devices['Desktop Chrome'],
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 10_000,
    navigationTimeout: 20_000,
  },
})
