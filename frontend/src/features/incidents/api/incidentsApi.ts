import type { CreateIncidentRequest, IncidentDetail, IncidentResponse } from '../types/incident.types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/** Error tipado que conserva el status HTTP sin exponer el cuerpo técnico del backend. */
export class IncidentApiError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`HTTP ${status}`);
    this.name = 'IncidentApiError';
    this.status = status;
  }
}

export async function createIncident(request: CreateIncidentRequest): Promise<IncidentResponse> {
  const response = await fetch(`${API_BASE}/api/v1/incidents`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new IncidentApiError(response.status);
  }

  return response.json();
}

/**
 * Consulta `GET /api/v1/incidents/{id}` (ver `docs/openapi: 3.0.yml`) — detalle
 * consolidado del incidente, incluyendo `analyses: IncidentAnalysis[]` con el
 * `suggestedScript` que consume `RemediationPanel` (LOG-US4-FE-04).
 */
export async function getIncidentDetail(incidentId: string): Promise<IncidentDetail> {
  const response = await fetch(`${API_BASE}/api/v1/incidents/${incidentId}`);

  if (!response.ok) {
    throw new IncidentApiError(response.status);
  }

  return response.json();
}
