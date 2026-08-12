import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
/** `frontend/e2e` -> repo root (donde vive `docker-compose.yml`). */
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const ROOT_COMPOSE_FILE = path.join(REPO_ROOT, 'docker-compose.yml');
const NATIVE_OLLAMA_OVERRIDE_FILE = path.join(__dirname, 'docker-compose.e2e-native-ollama.yml');

/**
 * Playwright `globalTeardown` (`LOG-US4-E2E-04`): complemento de
 * `global-setup.ts` — apaga únicamente lo que `global-setup.ts` efectivamente
 * levantó, según `process.env.E2E_INFRA_MODE` (mismo proceso raíz de Node que
 * corrió `globalSetup` dentro de esta invocación de `playwright test`).
 * Nunca edita `docker-compose.yml`, solo lo invoca.
 *
 * - `E2E_INFRA_MODE === 'skip'` (Plan B, Ollama nativo no detectado): no se
 *   levantó ninguna infraestructura — no hay nada que apagar.
 * - `E2E_INFRA_MODE === 'native-ollama'` (Plan A): se apagan `db` y `backend`
 *   con los mismos `-f` que usó `global-setup.ts`. Se usa `docker compose
 *   stop` (no `down`) para preservar el volumen `pgdata` entre corridas.
 */
export default async function globalTeardown(): Promise<void> {
  if (process.env.E2E_INFRA_MODE !== 'native-ollama') {
    // Plan B (bypass) u otro estado inesperado: nunca se levantó infraestructura.
    return;
  }

  try {
    execFileSync(
      'docker',
      ['compose', '-f', ROOT_COMPOSE_FILE, '-f', NATIVE_OLLAMA_OVERRIDE_FILE, 'stop', 'db', 'backend'],
      { cwd: REPO_ROOT, stdio: 'inherit' },
    );
  } catch (error) {
    // No relanzamos: un fallo al apagar no debe enmascarar el resultado real
    // de los tests, que ya corrieron para cuando este hook se invoca.
    console.error('[global-teardown] No se pudo detener la infraestructura Docker Compose:', error);
  }
}
