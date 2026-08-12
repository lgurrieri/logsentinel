import type { RemediationAction } from '../types/remediation.types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/** Error tipado que conserva el status HTTP sin exponer el cuerpo técnico del backend. */
export class RemediationApiError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`HTTP ${status}`);
    this.name = 'RemediationApiError';
    this.status = status;
  }
}

/**
 * Dispara `POST /api/v1/incidents/{id}/remediations` (ver `docs/openapi: 3.0.yml`).
 *
 * Sin `requestBody`: el backend deriva `generatedScript` leyendo
 * `IncidentAnalysis.suggestedScript` del diagnóstico persistido asociado al incidente
 * (decisión de contrato `LOG-US4-BE-02`, Opción B) — el cliente no envía el script.
 *
 * El endpoint es síncrono: la promesa recién se resuelve cuando el script ya terminó
 * de ejecutarse en el sandbox, con el `RemediationAction` final (`executionStatus`,
 * `stdoutLog`, `stderrLog` ya poblados). No hay estados intermedios que consultar.
 */
export async function executeRemediation(incidentId: string): Promise<RemediationAction> {
  const response = await fetch(`${API_BASE}/api/v1/incidents/${incidentId}/remediations`, {
    method: 'POST',
  });

  if (!response.ok) {
    throw new RemediationApiError(response.status);
  }

  return response.json();
}
