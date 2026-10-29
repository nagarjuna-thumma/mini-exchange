import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests against the real stack: Postgres from docker compose, the Spring Boot
 * backend with the demo profile (traders plus market-making bots), and the Vite dev server.
 * Build the backend jar first: `cd backend && mvn -DskipTests package`.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '*.e2e.ts',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
  webServer: [
    {
      command: 'java -jar ../backend/target/mini-exchange-0.1.0.jar --spring.profiles.active=demo',
      url: 'http://localhost:8080/actuator/health',
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'npm run dev',
      url: 'http://localhost:5173',
      reuseExistingServer: !process.env.CI,
    },
  ],
});
