import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  // Playwright y Vitest conviven en `frontend/`: se restringe el glob a
  // `*.spec.ts` para que Playwright nunca intente cargar los `*.test.tsx` de
  // Vitest (y viceversa, ver `vite.config.ts` -> `test.include`).
  testMatch: '**/*.spec.ts',
  timeout: 30_000,
  // `global-setup.ts`/`global-teardown.ts` levantan/apagan vía Docker Compose
  // la infraestructura que vive fuera de `frontend/` (db, ollama, backend) —
  // ver LOG-US4-E2E-04. Nunca editan `docker-compose.yml`, solo lo invocan.
  globalSetup: './e2e/global-setup.ts',
  globalTeardown: './e2e/global-teardown.ts',
  use: { baseURL: 'http://localhost:5173' },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    // En CI cada corrida debe arrancar su propio servidor de Vite desde cero.
    reuseExistingServer: !process.env.CI,
  },
});
