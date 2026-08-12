import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
/** `frontend/e2e/fixtures` -> repo root. */
const REPO_ROOT = path.resolve(__dirname, '..', '..', '..');

const DB_SERVICE = 'db';
const DB_USER = 'logsentinel';
const DB_NAME = 'logsentinel';

/**
 * Script de remediación determinístico usado como fixture E2E (`LOG-US4-E2E-04`).
 *
 * `echo` está en el allowlist por defecto del sandbox
 * (`logsentinel.sandbox.allowlist`, ver `ProcessBuilderSecuritySandbox` /
 * `LOG-US4-BE-01`) y la cadena no contiene ninguno de los 5 metacaracteres de
 * inyección Bash que `CommandAllowlist` rechaza (`| && $( \` >`), por lo que el
 * sandbox lo ejecuta sin objeciones y el proceso sale con código 0 — satisfaciendo
 * la rama "SUCCESS" / incidente "RESOLVED" del Gherkin de US4.
 */
export const FIXTURE_SUGGESTED_SCRIPT = "echo 'success'";

/**
 * Ejecuta `sql` contra la base de datos de la pila local levantada por
 * `global-setup.ts`, vía `docker compose exec` sobre el servicio `db` ya
 * definido en el `docker-compose.yml` raíz (nunca editado por este módulo).
 *
 * Se invoca `docker` con `execFileSync` (sin shell intermedio) para que el
 * texto de `sql` viaje como un único argumento literal — nada de interpolación
 * de shell que abra una superficie de inyección de comandos del lado del host.
 */
function runPsql(sql: string): string {
  return execFileSync(
    'docker',
    [
      'compose',
      'exec',
      '-T',
      DB_SERVICE,
      'psql',
      '-U',
      DB_USER,
      '-d',
      DB_NAME,
      '-v',
      'ON_ERROR_STOP=1',
      '-t', // tuples only: sin encabezados ni pie de resumen "(N rows)"
      '-A', // unaligned: sin padding de columnas
      '-c',
      sql,
    ],
    { cwd: REPO_ROOT, encoding: 'utf-8' },
  );
}

/**
 * Extrae el valor escalar de la salida de `runPsql`.
 *
 * `-t` (tuples-only) suprime encabezados y el pie "(N rows)", pero NO el tag
 * de estado de comando que `psql` imprime tras cada sentencia exitosa (ej.
 * `INSERT 0 1`, `SELECT 1`) — ese tag queda en una línea aparte, después del
 * valor. Tomar la primera línea no vacía aísla el valor real sin depender de
 * ese detalle de formato de `psql`.
 */
function firstNonEmptyLine(output: string): string {
  return (
    output
      .split('\n')
      .map((line) => line.trim())
      .find((line) => line.length > 0) ?? ''
  );
}

export interface SeededIncident {
  incidentId: string;
}

/**
 * Siembra directamente en la base de datos relacional (sin pasar por
 * `POST /incidents` ni disparar el pipeline real de RAG (US2) / LLM (US3)) un
 * incidente con un diagnóstico de IA ya persistido y un `suggestedScript`
 * determinístico — satisfaciendo la precondición del Gherkin de US4: "Dado que
 * existe un análisis guardado con un script de solución sugerido".
 *
 * Decisión aprobada para `LOG-US4-E2E-04`: US2/US3 ya tienen su propia
 * cobertura de tests, y el no-determinismo de una inferencia LLM real sería
 * indeseable en un E2E que busca validar el flujo de remediación de US4.
 */
export function seedIncidentWithSuggestedScript(): SeededIncident {
  const incidentId = firstNonEmptyLine(
    runPsql(
      "INSERT INTO incidents (system_name, urgency, raw_logs, status) " +
        "VALUES ('auth-service', 'CRITICAL', " +
        "'LOG-US4-E2E-04 fixture: connection pool exhausted', 'OPEN') " +
        'RETURNING id;',
    ),
  );

  // Dollar-quoting ($$...$$) evita tener que escapar las comillas simples de
  // `FIXTURE_SUGGESTED_SCRIPT` dentro del literal SQL.
  runPsql(
    'INSERT INTO incident_diagnostics (incident_id, diagnostic_text, suggested_script) ' +
      `VALUES ('${incidentId}', ` +
      "'LOG-US4-E2E-04 fixture: causa raiz simulada, sin invocar al LLM real.', " +
      `$$${FIXTURE_SUGGESTED_SCRIPT}$$);`,
  );

  return { incidentId };
}

/** Elimina en cascada (hijas primero) todo rastro del incidente sembrado por el test. */
export function cleanupIncident(incidentId: string): void {
  runPsql(
    `DELETE FROM remediation_actions WHERE incident_id = '${incidentId}'; ` +
      `DELETE FROM incident_diagnostics WHERE incident_id = '${incidentId}'; ` +
      `DELETE FROM incidents WHERE id = '${incidentId}';`,
  );
}

/**
 * Lee `incidents.status` directo de la base de datos — verificación de auditoría
 * independiente de la respuesta HTTP, para el paso del Gherkin "el estado del
 * incidente principal debe actualizarse automáticamente a RESOLVED".
 */
export function fetchIncidentStatus(incidentId: string): string {
  return firstNonEmptyLine(runPsql(`SELECT status FROM incidents WHERE id = '${incidentId}';`));
}

/**
 * Lee el `execution_status` de la remediación más reciente para el incidente —
 * verifica el paso del Gherkin "el registro se actualiza a estado SUCCESS o
 * FAILED según el código de salida del proceso" directamente contra
 * `remediation_actions`, sin depender únicamente del DOM.
 */
export function fetchLatestRemediationStatus(incidentId: string): string | null {
  const result = firstNonEmptyLine(
    runPsql(
      `SELECT execution_status FROM remediation_actions ` +
        `WHERE incident_id = '${incidentId}' ORDER BY created_at DESC LIMIT 1;`,
    ),
  );
  return result.length > 0 ? result : null;
}
