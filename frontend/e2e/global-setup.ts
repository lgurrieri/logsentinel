import { execFileSync } from 'node:child_process';
import { copyFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
/** `frontend/e2e` -> repo root (donde vive `docker-compose.yml`). */
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const BACKEND_ENV_PATH = path.join(REPO_ROOT, 'backend', '.env');
const BACKEND_ENV_EXAMPLE_PATH = path.join(REPO_ROOT, 'backend', '.env.example');
const ROOT_COMPOSE_FILE = path.join(REPO_ROOT, 'docker-compose.yml');
const NATIVE_OLLAMA_OVERRIDE_FILE = path.join(__dirname, 'docker-compose.e2e-native-ollama.yml');

const BACKEND_HEALTH_URL = 'http://localhost:8080/actuator/health';
const NATIVE_OLLAMA_TAGS_URL = 'http://localhost:11434/api/tags';
const NATIVE_OLLAMA_DETECT_TIMEOUT_MS = 2_000;
const POLL_INTERVAL_MS = 2_000;
/**
 * En el primer arranque de la pila con el `ollama` en contenedor (si no se
 * detecta Ollama nativo en el host, ver Plan B más abajo), el perfil `ollama`
 * (`pull-model-strategy: when_missing`, ver `application-ollama.yml`) descarga
 * `llama3.1` + `nomic-embed-text` antes de que el backend quede `UP` — puede
 * tardar varios minutos según el ancho de banda. Configurable vía
 * `E2E_BACKEND_STARTUP_TIMEOUT_MS` para CI/redes lentas.
 */
const DEFAULT_STARTUP_TIMEOUT_MS = 600_000;

/**
 * Valores posibles de `process.env.E2E_INFRA_MODE`, seteados por este
 * `globalSetup` y leídos tanto por `global-teardown.ts` (misma invocación de
 * `playwright test`, mismo proceso raíz de Node) como por los specs — los
 * workers de test que Playwright bifurca a partir de ese proceso raíz heredan
 * `process.env` tal como queda al finalizar `globalSetup`, que es el
 * mecanismo estándar documentado de Playwright para pasar datos desde
 * `globalSetup` hacia los tests.
 */
type InfraMode = 'native-ollama' | 'skip';

/**
 * Playwright `globalSetup` (`LOG-US4-E2E-04`): levanta automáticamente la
 * infraestructura local de pruebas que vive fuera de `frontend/` — PostgreSQL
 * y el backend Spring Boot — vía Docker Compose, antes de que corra la suite
 * E2E. El servidor de Vite (frontend) lo levanta Playwright mismo a través de
 * la opción `webServer` de `playwright.config.ts`.
 *
 * ### Plan A / Plan B (bypass del `ollama` en contenedor)
 * El cuello de botella real de este pipeline es el servicio `ollama` de
 * `docker-compose.yml`: en frío, descarga la imagen + los modelos `llama3.1`
 * y `nomic-embed-text` (~5GB), un costo de varios minutos u horas según el
 * ancho de banda (ver `DEBT-005` en `docs/deuda-tecnica.md`). Por eso, antes
 * de tocar Docker, se detecta si ya hay una instancia nativa de Ollama
 * corriendo en el host (`http://localhost:11434`, fuera de Docker, con los
 * modelos ya descargados):
 *
 * - **Plan A (nativo detectado):** se levantan solo `db` y `backend` vía
 *   Docker Compose con `--no-deps` (nunca se levanta el servicio `ollama` en
 *   contenedor, aunque `backend` lo liste en `depends_on`) y un archivo de
 *   override (`docker-compose.e2e-native-ollama.yml`, nunca se edita
 *   `docker-compose.yml`) que redirige `SPRING_AI_OLLAMA_BASE_URL` al Ollama
 *   nativo vía `host.docker.internal`.
 * - **Plan B (nativo NO detectado):** bypass explícito — no se levanta
 *   ninguna infraestructura Docker. Se comunica el motivo a los tests vía
 *   `process.env.E2E_INFRA_MODE = 'skip'` / `E2E_SKIP_REASON`, para que el
 *   spec llame a `test.skip(...)` con un mensaje claro en vez de fallar
 *   contra una infraestructura inexistente.
 *
 * Este script únicamente *invoca* `docker compose` (equivalente a lo que ya
 * documenta `agents.md` § "Setup del Entorno Local" para un desarrollador
 * humano) — nunca edita `docker-compose.yml`.
 */
export default async function globalSetup(): Promise<void> {
  ensureBackendEnvFile();

  const nativeOllamaAvailable = await detectNativeOllama();

  if (!nativeOllamaAvailable) {
    const reason =
      `Ollama nativo no detectado en ${NATIVE_OLLAMA_TAGS_URL} (timeout ${NATIVE_OLLAMA_DETECT_TIMEOUT_MS}ms). ` +
      'Bypass del E2E: no se levantó ninguna infraestructura Docker. Instalá/arrancá Ollama nativo ' +
      '(con los modelos `llama3.1` y `nomic-embed-text` ya descargados) o corré manualmente ' +
      '`docker compose up -d db ollama backend` (servicio `ollama` en contenedor) antes de esta suite.';
    setInfraMode('skip', reason);
    console.warn(`[global-setup] ${reason}`);
    return;
  }

  setInfraMode('native-ollama');
  startInfrastructureWithNativeOllama();
  await waitForBackendHealth();
}

/**
 * `docker-compose.yml` declara `env_file: ./backend/.env` para el servicio
 * `backend` — sin ese archivo, `docker compose up` falla incluso antes de
 * intentar levantar ningún contenedor. Bajo el perfil `ollama` (por defecto)
 * ninguna variable es realmente obligatoria, así que copiar la plantilla
 * `.env.example` alcanza (mismo paso que documenta `agents.md` para setup
 * local manual). `backend/.env` está en `.gitignore`: nunca se commitea.
 */
function ensureBackendEnvFile(): void {
  if (existsSync(BACKEND_ENV_PATH)) return;
  copyFileSync(BACKEND_ENV_EXAMPLE_PATH, BACKEND_ENV_PATH);
}

function setInfraMode(mode: InfraMode, reason?: string): void {
  process.env.E2E_INFRA_MODE = mode;
  if (reason) process.env.E2E_SKIP_REASON = reason;
}

/**
 * Reachability check contra el mismo endpoint que usa el healthcheck del
 * servicio `ollama` de `docker-compose.yml` (`ollama list`, equivalente HTTP:
 * `GET /api/tags`), pero apuntando al Ollama nativo del host.
 */
async function detectNativeOllama(): Promise<boolean> {
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), NATIVE_OLLAMA_DETECT_TIMEOUT_MS);
    const response = await fetch(NATIVE_OLLAMA_TAGS_URL, { signal: controller.signal });
    clearTimeout(timeoutId);
    if (!response.ok) return false;
    const body = (await response.json()) as { models?: unknown[] };
    return Array.isArray(body.models);
  } catch {
    // Timeout, conexión rechazada, o respuesta no-JSON: se asume no disponible.
    return false;
  }
}

function startInfrastructureWithNativeOllama(): void {
  try {
    execFileSync(
      'docker',
      [
        'compose',
        '-f',
        ROOT_COMPOSE_FILE,
        '-f',
        NATIVE_OLLAMA_OVERRIDE_FILE,
        'up',
        '-d',
        '--wait',
        '--build',
        '--no-deps',
        'db',
        'backend',
      ],
      { cwd: REPO_ROOT, stdio: 'inherit' },
    );
  } catch (error) {
    throw new Error(
      'No se pudo levantar la infraestructura local (docker compose up --no-deps db backend, ' +
        'con Ollama nativo del host). Verificá que Docker Desktop esté corriendo y que el puerto ' +
        `5432/8080 estén libres. Causa original: ${String(error)}`,
    );
  }
}

async function waitForBackendHealth(): Promise<void> {
  const timeoutMs = Number(process.env.E2E_BACKEND_STARTUP_TIMEOUT_MS ?? DEFAULT_STARTUP_TIMEOUT_MS);
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    if (await isBackendHealthy()) return;
    await sleep(POLL_INTERVAL_MS);
  }

  throw new Error(
    `El backend no reportó estado UP en ${BACKEND_HEALTH_URL} dentro de ${timeoutMs}ms. ` +
      'Revisá que el Ollama nativo del host tenga los modelos `llama3.1` y `nomic-embed-text` ya ' +
      'descargados (`curl http://localhost:11434/api/tags`) — si faltan, Spring AI intentará ' +
      'descargarlos él mismo al arrancar (`pull-model-strategy: when_missing`), lo cual puede ser lento. ' +
      "Para inspeccionar el progreso: `docker compose logs -f backend`.",
  );
}

async function isBackendHealthy(): Promise<boolean> {
  try {
    const response = await fetch(BACKEND_HEALTH_URL);
    if (!response.ok) return false;
    const body = (await response.json()) as { status?: string };
    return body.status === 'UP';
  } catch {
    // Backend todavía no acepta conexiones — se reintenta hasta el timeout.
    return false;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
