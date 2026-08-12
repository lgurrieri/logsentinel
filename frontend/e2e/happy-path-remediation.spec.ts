import { test, expect } from '@playwright/test';
import {
  seedIncidentWithSuggestedScript,
  cleanupIncident,
  fetchIncidentStatus,
  fetchLatestRemediationStatus,
  FIXTURE_SUGGESTED_SCRIPT,
  type SeededIncident,
} from './fixtures/incidentFixture';

/**
 * E2E happy path (`LOG-US4-E2E-04`): automatiza el escenario Gherkin de US4
 * ("Ejecución exitosa de un script de remediación generado por la IA") contra
 * la pila real -frontend + backend dockerizado + PostgreSQL- levantada por
 * `global-setup.ts`.
 *
 * ### Precondición: fixture en base de datos, no pipeline real
 * En vez de disparar el pipeline real de RAG (US2) / LLM (US3) -no
 * determinístico y ya cubierto por su propia suite de tests-, se siembra
 * directamente en la base de datos un incidente con un diagnóstico de IA ya
 * persistido y un `suggestedScript` fijo (decisión aprobada para este ticket).
 *
 * ### Por qué se intercepta el stream SSE de diagnóstico
 * `StreamDiagnosticService` invoca siempre el pipeline real de RAG/LLM al
 * recibir una petición en `GET .../diagnostic/stream`, sin importar si ya
 * existe un diagnóstico persistido para el incidente -e intentaría insertar
 * una segunda fila en `incident_diagnostics`, violando la restricción
 * `UNIQUE(incident_id)` que ya satisface la fila sembrada por la fixture-.
 * Se intercepta a nivel de red del navegador (`page.route`) para que esa
 * petición nunca llegue al backend; el resto de los endpoints (detalle del
 * incidente, ejecución de remediación) sí golpean el backend real.
 */
test.describe('US4 - Ejecución controlada de remediación (happy path)', () => {
  let seeded: SeededIncident | undefined;

  test.beforeEach(() => {
    // Bypass (Plan B, ver `global-setup.ts`): si no se detectó Ollama nativo en
    // el host, no se levantó ninguna infraestructura Docker -sembrar la
    // fixture contra una base de datos inexistente fallaría de forma opaca. Se
    // salta explícitamente con el motivo real en vez de reportar un falso rojo.
    test.skip(
      process.env.E2E_INFRA_MODE === 'skip',
      process.env.E2E_SKIP_REASON ?? 'Infraestructura E2E no disponible (E2E_INFRA_MODE=skip).',
    );
    seeded = seedIncidentWithSuggestedScript();
  });

  test.afterEach(() => {
    // Si el test se saltó en `beforeEach` (Plan B), `seeded` nunca se asignó.
    if (seeded) cleanupIncident(seeded.incidentId);
  });

  /**
   * `test.skip` en `beforeEach` garantiza en runtime que este test nunca
   * ejecute su cuerpo sin `seeded` asignado -pero TypeScript no puede inferir
   * esa garantía entre hooks-, así que se estrecha el tipo explícitamente acá
   * en vez de esparcir `seeded!.incidentId` por todo el cuerpo del test.
   */
  function requireSeeded(): SeededIncident {
    if (!seeded) {
      throw new Error('Fixture no sembrada: este test no debería ejecutar su cuerpo en modo skip.');
    }
    return seeded;
  }

  test('ejecuta el script sugerido, lo audita como SUCCESS y resuelve el incidente', async ({ page }) => {
    const { incidentId } = requireSeeded();

    await page.route('**/api/v1/incidents/*/diagnostic/stream', async (route) => {
      const chunk = JSON.stringify({
        chunk: 'Diagnóstico ya generado previamente (fixture E2E, LOG-US4-E2E-04).',
      });
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: `data: ${chunk}\n\n`,
      });
    });

    await page.goto(`/incidents/${incidentId}/dashboard`);

    // Precondición visible en la UI: el script sugerido por la IA ya está cargado
    // (viene del fetch real a `GET /api/v1/incidents/{id}` contra el backend
    // dockerizado, resuelto contra la fila sembrada por la fixture).
    await expect(page.getByTestId('remediation-code-block')).toContainText(FIXTURE_SUGGESTED_SCRIPT);

    const runButton = page.getByRole('button', { name: 'Ejecutar Script de Remediación' });
    await expect(runButton).toBeEnabled();
    await runButton.click();

    // Doble confirmación (LOG-US4-FE-03): el CTA no dispara el script directo.
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText(
      '¿Confirmas la ejecución de este comando en el sistema de producción?',
    );
    await dialog.getByRole('button', { name: 'Confirmar Ejecución' }).click();

    const outputTerminal = page.getByRole('region', { name: 'Monitor de salida de la remediación' });

    // "el backend crea de inmediato un registro ... en estado EXECUTING": el
    // reducer del frontend despacha `START_EXECUTION` de forma síncrona antes de
    // esperar la respuesta HTTP (`useRemediationExecutor.confirmExecution`), y el
    // primer paso del backend real (`RemediationStateMachine.commitExecuting`,
    // transacción `REQUIRES_NEW`) es justamente comitear esa fila EXECUTING antes
    // de invocar el sandbox -por lo que este estado transitorio de la UI es un
    // proxy observable y confiable de esa garantía transaccional. Verificar la
    // fila EXECUTING directamente en la base de datos no es viable acá: la
    // ejecución real (`echo`) resuelve en pocos milisegundos, una ventana más
    // angosta que la latencia de una consulta SQL vía `docker compose exec`; esa
    // transición atómica ya está cubierta por los tests de integración del
    // backend (`RemediationStateMachine`).
    await expect(outputTerminal).toContainText('Ejecutando script en la infraestructura simulada');

    // Resultado final: la ejecución real del sandbox (`echo 'success'`, validado
    // contra el allowlist por defecto) sale con código de salida 0.
    await expect(outputTerminal).toContainText('✓ Ejecución exitosa', { timeout: 15_000 });
    await expect(outputTerminal).toContainText('success');

    // Auditoría persistida en base de datos - verificación independiente del DOM.
    await expect
      .poll(() => fetchLatestRemediationStatus(incidentId), { timeout: 15_000 })
      .toBe('SUCCESS');
    await expect.poll(() => fetchIncidentStatus(incidentId), { timeout: 15_000 }).toBe('RESOLVED');
  });
});
